import { defineCommerceJourney } from "./support/commerce-journey";
import * as fixtures from "./support/cloud-fixtures";
import { cloudTarget } from "./support/cloud-target";
const target = cloudTarget(process.env);
defineCommerceJourney(target.ignoreHTTPSErrors ? "isolated local TLS: four-role signed demo journey" : "deployed HTTPS: four-role signed demo journey", { ...fixtures, secureCookies: true, ignoreHTTPSErrors: target.ignoreHTTPSErrors });
