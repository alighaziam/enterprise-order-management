package io.github.alighaziam.eom.operations.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OperatorActionRepository extends JpaRepository<OperatorActionEntity, UUID> {

    @Query("""
            select action from OperatorActionEntity action
            where (:q is null or :q = ''
                or lower(action.actor) like lower(concat('%', :q, '%'))
                or lower(action.action) like lower(concat('%', :q, '%'))
                or lower(action.targetType) like lower(concat('%', :q, '%'))
                or lower(action.targetId) like lower(concat('%', :q, '%')))
              and (:outcome is null or :outcome = '' or action.outcome = :outcome)
            """)
    Page<OperatorActionEntity> search(@Param("q") String query,
                                      @Param("outcome") String outcome,
                                      Pageable pageable);
}
