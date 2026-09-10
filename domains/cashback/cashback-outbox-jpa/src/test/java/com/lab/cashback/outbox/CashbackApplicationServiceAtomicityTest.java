package com.lab.cashback.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O PONTO CENTRAL deste arquivo: {@code @DataJpaTest} por padrão embrulha CADA teste numa
 * transação que sofre rollback automático no final -- ótimo para isolar testes entre si,
 * péssimo pra testar rollback de verdade, porque o método @Transactional do serviço
 * (propagation REQUIRED, o padrão) apenas ENTRA na transação que o teste já abriu, em vez
 * de abrir a sua própria. Resultado: uma exceção lá dentro não "desfaz e você vê o
 * antes" -- ela marca a transação (a mesma, do teste inteiro) como rollback-only, e
 * qualquer leitura seguinte ainda enxerga o objeto gerenciado em memória pelo Hibernate,
 * não o que está (ou não está) realmente no banco. Foi exatamente essa mistura de
 * "gerenciado vs não gerenciado" que gerava resultado falso-positivo.
 *
 * A CORREÇÃO não é uma anotação a mais -- é uma a menos, efetivamente: usamos
 * {@code @Transactional(propagation = Propagation.NOT_SUPPORTED)} na classe do teste para
 * DESLIGAR esse embrulho automático do @DataJpaTest. Com isso, cada chamada ao serviço
 * abre e fecha sua PRÓPRIA transação de verdade (igual em produção), e cada leitura de
 * verificação depois também abre a sua -- sem cache de persistence context vazando de uma
 * chamada pra outra.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // usa o H2 do application.yml do projeto, não troca por outro
@Import(CashbackApplicationService.class) // @DataJpaTest só escaneia @Entity/@Repository por padrão
@Transactional(propagation = Propagation.NOT_SUPPORTED) // <- a "anotação" que resolve: desliga o embrulho transacional do teste
class CashbackApplicationServiceAtomicityTest {

    @Autowired CashbackApplicationService service;
    @Autowired BalanceRepository balances;
    @Autowired OutboxEventRepository outbox;

    @Test
    void appliesCashbackAndRecordsOutboxTogether() {
        var result = service.applyCashback("tx-100", "user-1", 150L);

        assertEquals(CashbackApplicationService.ApplyResult.APPLIED_NOW, result);

        // Cada chamada de repositório abaixo abre sua própria transação (o service já
        // fechou/committou a dele) -- isto está lendo o banco de verdade, não um cache.
        assertEquals(150L, balances.findById("user-1").orElseThrow().getAmountCents());
        assertTrue(outbox.findByCorrelationId("tx-100").isPresent());
        assertEquals(OutboxStatus.PENDING, outbox.findByCorrelationId("tx-100").orElseThrow().getStatus());
    }

    @Test
    void sameTransactionIdIsNeverAppliedTwice() {
        service.applyCashback("tx-101", "user-2", 100L);
        var second = service.applyCashback("tx-101", "user-2", 100L); // redelivery simulada

        assertEquals(CashbackApplicationService.ApplyResult.ALREADY_APPLIED, second);
        assertEquals(100L, balances.findById("user-2").orElseThrow().getAmountCents(),
                "saldo não pode ter sido incrementado duas vezes pela mesma transação");
    }

    @Test
    void concurrentDuplicateLosesRaceViaUniqueConstraintNotViaPreCheck() {
        // Simula a corrida de verdade: insere o outbox de tx-102 diretamente, ignorando
        // a checagem otimista do serviço -- isso prova que é a constraint UNIQUE (e não
        // o "if já existe?" em CashbackApplicationService) quem realmente impede a
        // duplicata sob concorrência.
        outbox.save(new OutboxEvent("tx-102", "CashbackApplied", "{}"));

        assertThrows(DataIntegrityViolationException.class, () ->
                outbox.saveAndFlush(new OutboxEvent("tx-102", "CashbackApplied", "{}")),
            "a constraint UNIQUE em correlationId deveria rejeitar a segunda inserção");
    }
}

/*
 * ANTI-EXEMPLO (não incluído como teste real de propósito -- só documentado aqui):
 *
 *   @DataJpaTest
 *   @Import(CashbackApplicationService.class)
 *   class ArmadilhaTest {
 *       @Test
 *       void issoMenteQuePassa() {
 *           // SEM o @Transactional(NOT_SUPPORTED) na classe, este teste roda dentro da
 *           // transação que o @DataJpaTest já abriu. Se você forçar uma falha no
 *           // outbox.save() (ex.: via @SpyBean) DEPOIS do balances.save() ter rodado,
 *           // a chamada a service.applyCashback(...) lança a exceção e a transação
 *           // (a mesma do teste inteiro) é marcada rollback-only -- mas o objeto
 *           // `Balance` que você buscou ANTES ainda está gerenciado em memória com o
 *           // valor incrementado. Um assertEquals(150L, ...) nesse objeto em memória
 *           // passaria, te convencendo de que o saldo foi persistido -- quando na
 *           // verdade nada foi de fato commitado no banco. É esse falso-positivo que
 *           // o @Transactional(NOT_SUPPORTED) na classe do teste evita.
 *       }
 *   }
 */
