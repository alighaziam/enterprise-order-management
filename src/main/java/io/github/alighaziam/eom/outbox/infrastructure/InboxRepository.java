package io.github.alighaziam.eom.outbox.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InboxRepository extends JpaRepository<InboxEntity,String>{}