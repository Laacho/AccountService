package com.banking.account.feing.service;

import com.banking.account.feing.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserClientService {

    private final UserServiceClient userServiceClient;

    @Autowired
    public UserClientService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }


    public void validateUserExists(UUID userId) {
        ResponseEntity<Void> voidResponseEntity = userServiceClient.userExists(userId);
        if(voidResponseEntity.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            //todo handle exceptions
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

    }

}
