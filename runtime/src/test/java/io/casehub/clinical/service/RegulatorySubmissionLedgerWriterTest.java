package io.casehub.clinical.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.RegulatorySubmissionLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegulatorySubmissionLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks RegulatorySubmissionLedgerWriter writer;

    @Test
    void grade5_writes_entry_with_c1i_planRef_and_correct_fields() {
        final Instant now = Instant.parse("2026-06-15T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_5;
        ae.tenantId = "test-tenant";

        writer.writeEntry(ae);

        verify(ledgerEntryRepository).save(
                argThat(entry -> {
                    RegulatorySubmissionLedgerEntry rsle = (RegulatorySubmissionLedgerEntry) entry;
                    return rsle.aeId.equals(ae.id)
                            && "GRADE_5".equals(rsle.grade)
                            && rsle.filedAt.equals(now)
                            && rsle.subjectId.equals(ae.enrollmentId)
                            && rsle.sequenceNumber == 1
                            && rsle.id != null
                            && rsle.compliance()
                                    .map(c -> c.planRef)
                                    .orElse("")
                                    .contains("(c)(1)(i)")
                            && !rsle.compliance()
                                    .map(c -> c.planRef)
                                    .orElse("")
                                    .contains("(c)(1)(ii)");
                }),
                eq("default"));
    }

    @Test
    void grade3_writes_entry_with_c1ii_planRef() {
        final Instant now = Instant.parse("2026-06-16T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.tenantId = "test-tenant";

        writer.writeEntry(ae);

        verify(ledgerEntryRepository).save(
                argThat(entry -> {
                    RegulatorySubmissionLedgerEntry rsle = (RegulatorySubmissionLedgerEntry) entry;
                    return rsle.aeId.equals(ae.id)
                            && "GRADE_3".equals(rsle.grade)
                            && rsle.filedAt.equals(now)
                            && rsle.subjectId.equals(ae.enrollmentId)
                            && rsle.sequenceNumber == 1
                            && rsle.id != null
                            && rsle.compliance()
                                    .map(c -> c.planRef)
                                    .orElse("")
                                    .contains("(c)(1)(ii)");
                }),
                eq("default"));
    }
}
