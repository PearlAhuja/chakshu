package com.freecharge.smsprofilerservice.aws.accessor;

import com.freecharge.smsprofilerservice.response.GetBlacklistedSendersAndCheckpointResponse;
import com.freecharge.smsprofilerservice.response.GetBlacklistedUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingServiceAccessor {

    private final RestTemplate restTemplate;
    @Value("${parsing.service.blacklisted.sender.url}")
    private String urlForBlackListedSender;

    @Value("${parsing.service.blacklisted.user.url}")
    private String urlForBlackListedUser;

    public List<String> getBlackListedSenders() {
        return Optional.ofNullable(restTemplate.getForObject(urlForBlackListedSender, GetBlacklistedSendersAndCheckpointResponse.class))
                .map(GetBlacklistedSendersAndCheckpointResponse::getBlacklistedSenders)
                .orElseGet(() -> null);
    }

    public boolean isBlackListedUser(String userId) {
        return Optional.ofNullable(restTemplate.getForObject(urlForBlackListedUser + "?userId=" + userId, GetBlacklistedUserResponse.class))
                .map(GetBlacklistedUserResponse::isBlacklistedUser)
                .orElseGet(() -> false);
    }
}
