package com.sender.backend.presence;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

@Configuration
public class PresenceConfig {
    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "memory")
    PresenceSessionStore inMemoryPresenceSessionStore(
            @Value("${app.presence.session-ttl:90s}") Duration ttl) {
        return new InMemoryPresenceSessionStore(ttl);
    }

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "memory")
    PresenceNotifier localPresenceNotifier(FriendshipPresenceFanout fanout) {
        return new LocalPresenceNotifier(fanout);
    }

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "redis", matchIfMissing = true)
    PresenceSessionStore redisPresenceSessionStore(
            StringRedisTemplate redis,
            @Value("${app.presence.session-ttl:90s}") Duration ttl) {
        return new RedisPresenceSessionStore(redis, ttl);
    }

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "redis", matchIfMissing = true)
    PresenceNotifier redisPresenceNotifier(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisPresenceNotifier(redis, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "redis", matchIfMissing = true)
    RedisMessageListenerContainer presenceListenerContainer(
            RedisConnectionFactory connectionFactory,
            FriendshipPresenceFanout fanout,
            ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                new PresenceTransitionListener(fanout, objectMapper),
                new ChannelTopic(RedisPresenceNotifier.CHANNEL));
        return container;
    }

    @Bean
    PresenceExpiryScheduler presenceExpiryScheduler(PresenceService presence) {
        return new PresenceExpiryScheduler(presence);
    }

    public static class PresenceExpiryScheduler {
        private final PresenceService presence;

        PresenceExpiryScheduler(PresenceService presence) {
            this.presence = presence;
        }

        @Scheduled(fixedDelayString = "${app.presence.sweep-ms:15000}")
        public void sweep() {
            presence.expireStaleSessions();
        }
    }
}
