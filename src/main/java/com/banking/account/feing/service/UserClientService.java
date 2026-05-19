package com.banking.account.feing.service;

import com.banking.account.aspect.Logged;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.feing.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Logged
@Service
public class UserClientService {

    private final UserServiceClient userServiceClient;

    @Autowired
    public UserClientService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    public void validateUserExists(UUID userId) {
        try {
            userServiceClient.userExists(userId);
        } catch (feign.FeignException.NotFound e) {
            throw new AccountNotFoundException("User not found: " + userId);
        } catch (feign.FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User-Service unavailable");
        }
    }

}