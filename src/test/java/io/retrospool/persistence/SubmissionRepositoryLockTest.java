package io.retrospool.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

class SubmissionRepositoryLockTest {

    @Test
    void decisionReadUsesAPessimisticWriteLock() throws Exception {
        var method = SubmissionRepository.class.getMethod("findByIdForUpdate", UUID.class);

        assertThat(method.getAnnotation(Lock.class)).isNotNull();
        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class)).isNotNull();
    }
}
