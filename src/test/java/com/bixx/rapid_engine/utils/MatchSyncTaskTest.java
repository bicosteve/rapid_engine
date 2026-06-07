package com.bixx.rapid_engine.utils;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.producer.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class MatchSyncTaskTest {

    @Mock
    private RundownConfig rundownConfig;
    @Mock
    private EventProducer eventProducer;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private MatchSyncTask task;

    @BeforeEach
    void setUp(){
        task = new MatchSyncTask(rundownConfig, eventProducer, stringRedisTemplate);
    }

    // =========================================================================
    // Guard clauses
    // =========================================================================

    @Test
    @DisplayName("fetchMatches: returns early when sportIds list is null")
    void fetchMatches_returnsEarlyWhenSportIdsNull(){
        when(rundownConfig.getSportsId()).thenReturn(null);

        task.fetchMatches();

        verify(eventProducer, never()).fetchEvents(anyInt());
    }

    @Test
    @DisplayName("fetchMatches: returns early when sportIds list is empty")
    void fetchMatches_returnsEarlyWhenSportIdsEmpty(){
        when(rundownConfig.getSportsId()).thenReturn(List.of());

        task.fetchMatches();

        verify(eventProducer, never()).fetchEvents(anyInt());
    }

    // =========================================================================
    // Round-robin cycling
    // =========================================================================

    @Test
    @DisplayName("fetchMatches: first call uses sportIds[0]")
    void fetchMatches_firstCallUsesIndexZero(){
        when(rundownConfig.getSportsId()).thenReturn(List.of(1, 2, 3));
        when(eventProducer.fetchEvents(1)).thenReturn(10);

        task.fetchMatches();

        verify(eventProducer, times(1)).fetchEvents(1);
        verify(eventProducer, never()).fetchEvents(2);
        verify(eventProducer, never()).fetchEvents(3);
    }

    @Test
    @DisplayName("fetchMatches: subsequent calls increment the index in order")
    void fetchMatches_cyclesThroughAllSportIdsInOrder(){
        when(rundownConfig.getSportsId()).thenReturn(List.of(1, 2, 3));
        when(eventProducer.fetchEvents(anyInt())).thenReturn(0);

        task.fetchMatches();
        task.fetchMatches();
        task.fetchMatches();

        verify(eventProducer, times(1)).fetchEvents(1);
        verify(eventProducer, times(1)).fetchEvents(2);
        verify(eventProducer, times(1)).fetchEvents(3);
    }

    @Test
    @DisplayName("fetchMatches: index wraps back to 0 after the last sport")
    void fetchMatches_indexWrapsAround(){
        when(rundownConfig.getSportsId()).thenReturn(List.of(10, 20));
        when(eventProducer.fetchEvents(anyInt())).thenReturn(0);

        task.fetchMatches(); // 10
        task.fetchMatches(); // 20
        task.fetchMatches(); // 10 (wrapped)
        task.fetchMatches(); // 20 (wrapped)

        verify(eventProducer, times(2)).fetchEvents(10);
        verify(eventProducer, times(2)).fetchEvents(20);
    }

    @Test
    @DisplayName("fetchMatches: works correctly with a single-element sport list")
    void fetchMatches_singleSportList(){
        when(rundownConfig.getSportsId()).thenReturn(List.of(42));
        when(eventProducer.fetchEvents(42)).thenReturn(7);

        task.fetchMatches();
        task.fetchMatches();
        task.fetchMatches();

        verify(eventProducer, times(3)).fetchEvents(42);
    }

    @Test
    @DisplayName("fetchMatches: index does not drift when producer throws (caller does not catch)")
    void fetchMatches_indexStillIncrementsWhenProducerReturnsZero(){
        when(rundownConfig.getSportsId()).thenReturn(List.of(5, 6));
        when(eventProducer.fetchEvents(anyInt())).thenReturn(0);

        task.fetchMatches(); // 5
        task.fetchMatches(); // 6
        task.fetchMatches(); // 5 (wrapped)

        verify(eventProducer, times(2)).fetchEvents(5);
        verify(eventProducer, times(1)).fetchEvents(6);
    }

    @Test
    @DisplayName("fetchMatches: producer is invoked with the exact Integer from the config list")
    void fetchMatches_preservesExactSportIdValue(){
        when(rundownConfig.getSportsId()).thenReturn(Arrays.asList(100, 200));
        when(eventProducer.fetchEvents(anyInt())).thenReturn(0);

        task.fetchMatches();

        verify(eventProducer, times(1)).fetchEvents(100);
    }
}
