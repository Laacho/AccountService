package com.banking.account.feing.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "${services.user.url}")
public interface UserServiceClient {
    @GetMapping("/users/{userId}/exists")
    ResponseEntity<Void> userExists(@PathVariable UUID userId);
}
