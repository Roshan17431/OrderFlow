package com.orderflow.payment.provider;
public record ProviderPaymentStatus(State state,String failureCode){public enum State{SUCCEEDED,FAILED,PENDING,UNKNOWN}}
