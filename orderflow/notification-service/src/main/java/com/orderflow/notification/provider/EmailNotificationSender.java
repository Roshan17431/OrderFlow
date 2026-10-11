package com.orderflow.notification.provider;public interface EmailNotificationSender{SendResult send(String destination,String title,String message);}
