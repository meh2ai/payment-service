package com.payment.service;

import com.payment.api.model.AccountRequest;
import com.payment.api.model.AccountResponse;
import com.payment.exception.PaymentException;
import com.payment.model.Account;
import com.payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        UUID accountId = request.getAccountId() != null ? request.getAccountId() : UUID.randomUUID();
        BigDecimal balance = new BigDecimal(request.getBalance());

        Account account = new Account(accountId, balance, request.getCurrency());
        accountRepository.save(account);

        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(() -> PaymentException.accountNotFound(accountId));
        return toAccountResponse(account);
    }

    private AccountResponse toAccountResponse(Account account) {
        return modelMapper.map(account, AccountResponse.class);
    }
}
