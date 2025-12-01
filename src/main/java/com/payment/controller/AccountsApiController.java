package com.payment.controller;

import com.payment.api.AccountsApi;
import com.payment.api.model.AccountRequest;
import com.payment.api.model.AccountResponse;
import com.payment.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountsApiController implements AccountsApi {

    private final AccountService accountService;

    @Override
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        AccountResponse response = accountService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }
}
