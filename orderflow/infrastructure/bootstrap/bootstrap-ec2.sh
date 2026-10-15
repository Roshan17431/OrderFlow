#!/usr/bin/env bash
# Ubuntu 24.04 only. Root, explicit approved encrypted EBS volume required.
# Invoked by Terraform cloud-init, or manually with the same guarded inputs.
set -euo pipefail
umask 077
[[ $(id -u) == 0 ]] || { echo 'Run as root' >&2; exit 1; }
source /etc/os-release
[[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || { echo 'Ubuntu 24.04 required' >&2; exit 1; }
: "${EBS_VOLUME_ID:?Expected persistent EBS volume ID required}"
: "${EBS_ENCRYPTION_VERIFIED:?Set true only after verifying EBS encryption}"
[[ $EBS_ENCRYPTION_VERIFIED == true ]] || { echo 'Encrypted volume verification required' >&2; exit 1; }
DATA_MOUNT=${DATA_MOUNT:-/opt/orderflow}
[[ $DATA_MOUNT == /opt/orderflow ]] || { echo 'Expected /opt/orderflow mount' >&2; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt_safe() { timeout 900 apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 "$@"; }
apt_safe update
apt_safe install -y ca-certificates curl gnupg jq python3 unzip chrony unattended-upgrades nvme-cli e2fsprogs
# Resolve Nitro NVMe by the exact requested EBS serial; do not guess /dev/nvme1n1.
expected_serial=${EBS_VOLUME_ID//-/}
DATA_DEVICE=''
for attempt in $(seq 1 60); do
  while read -r device serial; do
    [[ ${serial//-/} == "$expected_serial" ]] && DATA_DEVICE=$device
  done < <(lsblk -dnpo NAME,SERIAL)
  [[ -n $DATA_DEVICE && -b $DATA_DEVICE ]] && break
  sleep 2
done
[[ -n $DATA_DEVICE && -b $DATA_DEVICE ]] || { echo 'Expected EBS volume not found; refusing guessed device' >&2; exit 1; }
# Encryption cannot be established from filesystem signatures. The caller must
# attest it from the encrypted aws_ebs_volume or a read-only AWS DescribeVolumes.
if mountpoint -q "$DATA_MOUNT"; then
  [[ $(findmnt -nro SOURCE --target "$DATA_MOUNT") == "$DATA_DEVICE" ]] || { echo 'Unexpected mounted device' >&2; exit 1; }
else
  [[ $(lsblk -nrpo NAME "$DATA_DEVICE" | wc -l) -eq 1 ]] || { echo 'Partitioned volume: operator must inspect/mount existing partition' >&2; exit 1; }
  [[ -z $(lsblk -nrpo MOUNTPOINTS "$DATA_DEVICE" | tr -d '[:space:]') ]] || { echo 'Device mounted elsewhere' >&2; exit 1; }
  fstype=$(blkid -s TYPE -o value "$DATA_DEVICE" || true)
  if [[ -z $fstype ]]; then
    [[ ${ALLOW_FORMAT_EMPTY_VOLUME:-false} == true ]] || { echo 'Blank volume formatting needs explicit authorization' >&2; exit 1; }
    [[ -z $(wipefs --no-act --output TYPE --noheadings "$DATA_DEVICE") ]] || { echo 'Existing signature; refusing format' >&2; exit 1; }
    mkfs.ext4 "$DATA_DEVICE"
    fstype=ext4
  fi
  [[ $fstype == ext4 ]] || { echo 'Existing non-ext4 filesystem preserved; operator required' >&2; exit 1; }
  if [[ -d $DATA_MOUNT && -n $(ls -A "$DATA_MOUNT") ]]; then
    echo 'Mount directory contains data; refusing to hide it' >&2; exit 1
  fi
  mkdir -p "$DATA_MOUNT"
  uuid=$(blkid -s UUID -o value "$DATA_DEVICE")
  [[ -n $uuid ]] || exit 1
  if grep -Eq "[[:space:]]${DATA_MOUNT}[[:space:]]" /etc/fstab; then
    grep -Eq "^UUID=${uuid}[[:space:]]+${DATA_MOUNT}[[:space:]]" /etc/fstab || { echo 'Conflicting fstab entry' >&2; exit 1; }
  else
    printf 'UUID=%s %s ext4 defaults,nofail 0 2\n' "$uuid" "$DATA_MOUNT" >> /etc/fstab
  fi
  mount "$DATA_MOUNT"
fi
install -d -m 0755 /etc/apt/keyrings
if [[ ! -s /etc/apt/keyrings/docker.asc ]]; then
  curl --fail --silent --show-error --connect-timeout 10 --max-time 60 https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod 0644 /etc/apt/keyrings/docker.asc
fi
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable\n' "$(dpkg --print-architecture)" > /etc/apt/sources.list.d/docker.list
apt_safe update
docker_package_state=$(mktemp)
trap 'rm -f "$docker_package_state"' EXIT
if dpkg-query -W -f='${Status}\n' docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >"$docker_package_state" 2>/dev/null && ! grep -qv 'install ok installed' "$docker_package_state"; then
  : # All installed: avoid unrequested daemon restart from a package upgrade.
else
  apt_safe install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
# Preserve unrelated daemon settings; update only explicit rotation settings.
install -d -m 0755 /etc/docker
python3 - <<'PY'
import json, os
p='/etc/docker/daemon.json'
c=json.load(open(p)) if os.path.exists(p) else {}
c['log-driver']='json-file'; c['log-opts']={'max-size':'10m','max-file':'3'}
tmp=p+'.tmp'
with open(tmp,'w') as f:json.dump(c,f,indent=2)
os.chmod(tmp,0o644); os.replace(tmp,p)
PY
systemctl enable --now docker chrony
# Do not restart an existing Docker daemon or interrupt existing applications.
if ! snap list amazon-ssm-agent >/dev/null 2>&1; then timeout 300 snap install amazon-ssm-agent --classic; fi
timeout 60 snap start --enable amazon-ssm-agent
# Official AWS-supported snap: snapd selects the host architecture and verifies
# signed store assertions/content integrity; never use --dangerous/sideloading.
case $(dpkg --print-architecture) in amd64|arm64) ;; *) echo 'Unsupported AWS CLI host architecture' >&2; exit 1;; esac
if command -v aws >/dev/null 2>&1; then
  timeout 15 aws --version 2>&1 | grep -q '^aws-cli/2\.' || { echo 'Existing AWS CLI is not v2; operator migration required' >&2; exit 1; }
else
  if ! snap list aws-cli >/dev/null 2>&1; then timeout 300 snap install aws-cli --classic; fi
  timeout 15 /snap/bin/aws --version 2>&1 | grep -q '^aws-cli/2\.' || { echo 'AWS CLI v2 installation verification failed' >&2; exit 1; }
  # SSM non-login shells may omit /snap/bin from PATH.
  [[ ! -e /usr/local/bin/aws && ! -L /usr/local/bin/aws ]] || { echo 'Existing AWS CLI path preserved; operator inspection required' >&2; exit 1; }
  ln -s /snap/bin/aws /usr/local/bin/aws
fi
id orderflow >/dev/null 2>&1 || useradd --create-home --shell /bin/bash orderflow
# Docker access equals root; keep deployment via audited SSM sudo instead of
# granting every login session permanent docker-group membership.
install -d -m 0700 "$DATA_MOUNT/config" "$DATA_MOUNT/backups"
install -d -m 0755 "$DATA_MOUNT/releases" "$DATA_MOUNT/data"
for dir in postgres redis kafka caddy/data caddy/config caddy/logs prometheus grafana node-exporter; do
  install -d -m 0755 "$DATA_MOUNT/data/$dir"
done
# Only empty/new paths receive known image ownership; never recursive-chown data.
for pair in 'postgres:70' 'redis:999' 'kafka:1000' 'prometheus:65534' 'grafana:472'; do
  dir=${pair%:*}; uid=${pair#*:}
  [[ -z $(ls -A "$DATA_MOUNT/data/$dir") ]] && chown "$uid:$uid" "$DATA_MOUNT/data/$dir"
done
cat > /etc/apt/apt.conf.d/52orderflow-security-updates <<'APT'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
Unattended-Upgrade::Automatic-Reboot "false";
APT
printf 'Bootstrap ready. Populate protected runtime.env and persistent key files; no application deployed.\n'
# These assets are supplied only by the dedicated Terraform cloud-demo configuration.
if [[ ${ORDERFLOW_ENABLE_ON_DEMAND:-false} == true ]]; then
  [[ -f /etc/orderflow-demo.json && -f /usr/local/lib/orderflow/on_demand_host.py ]] || { echo 'Missing dedicated on-demand assets' >&2; exit 1; }
  cat > /usr/local/sbin/orderflow-host <<'HOST'
#!/usr/bin/env bash
set -euo pipefail
exec /usr/bin/python3 /usr/local/lib/orderflow/on_demand_host.py "$@"
HOST
  chmod 0700 /usr/local/sbin/orderflow-host
  if [[ ! -e /etc/default/orderflow-demo ]]; then
    python3 - <<'DEFAULTS'
import json, pathlib
config=json.loads(pathlib.Path('/etc/orderflow-demo.json').read_text())
value=float(config['maxRuntimeHours'])
assert .25 <= value <= 24
p=pathlib.Path('/etc/default/orderflow-demo')
p.write_text('ORDERFLOW_DEMO_MAX_RUNTIME_HOURS='+str(value)+'\n');p.chmod(0o600)
DEFAULTS
  fi
  # Enable the pre-Docker replay guard only after CLI/controller installation.
  install -m 0600 /dev/null /etc/orderflow-demo-installed
  systemctl daemon-reload
  systemctl enable orderflow-demo.service orderflow-demo-stop.timer
  systemctl start orderflow-demo-stop.timer
  # Never start an application here: its first deployment still requires release gates.
  echo 'Dedicated on-demand boot service and independent maximum-runtime countdown enabled.'
fi
