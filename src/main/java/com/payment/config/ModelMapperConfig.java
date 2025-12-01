package com.payment.config;

import com.payment.api.model.AccountResponse;
import com.payment.api.model.PaymentAcceptedResponse;
import com.payment.api.model.PaymentResponse;
import com.payment.model.Account;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration for ModelMapper, a library that simplifies object-to-object mapping.
 * <p>
 * ModelMapper is used in this application to map between domain entities and API response DTOs.
 * It reduces boilerplate code by automatically mapping properties with matching names.
 *
 * @see org.modelmapper.ModelMapper
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        Converter<BigDecimal, String> bigDecimalToString =
            ctx -> ctx.getSource() != null ? ctx.getSource().toPlainString() : null;

        Converter<Enum<?>, String> enumToString =
            ctx -> ctx.getSource() != null ? ctx.getSource().name() : null;

        Converter<PaymentStatus, com.payment.api.model.PaymentStatus> paymentStatusConverter =
            ctx -> ctx.getSource() != null ? com.payment.api.model.PaymentStatus.valueOf(ctx.getSource().name()) : null;

        // Account -> AccountResponse
        mapper.createTypeMap(Account.class, AccountResponse.class)
            .addMapping(Account::getId, AccountResponse::setAccountId)
            .addMappings(m -> m.using(bigDecimalToString).map(Account::getBalance, AccountResponse::setBalance));

        // Payment -> PaymentAcceptedResponse
        mapper.createTypeMap(Payment.class, PaymentAcceptedResponse.class)
            .addMapping(Payment::getId, PaymentAcceptedResponse::setPaymentId)
            .addMappings(m -> m.using(paymentStatusConverter).map(Payment::getStatus, PaymentAcceptedResponse::setStatus));

        // Payment -> PaymentResponse
        mapper.createTypeMap(Payment.class, PaymentResponse.class)
            .addMapping(Payment::getId, PaymentResponse::setPaymentId)
            .addMappings(m -> {
                m.using(bigDecimalToString).map(Payment::getAmount, PaymentResponse::setAmount);
                m.using(paymentStatusConverter).map(Payment::getStatus, PaymentResponse::setStatus);
                m.using(enumToString).map(Payment::getErrorCode, PaymentResponse::setErrorCode);
            });

        return mapper;
    }
}
