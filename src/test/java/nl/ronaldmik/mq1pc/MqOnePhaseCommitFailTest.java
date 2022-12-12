package nl.ronaldmik.mq1pc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;

@SpringBootTest
class MqOnePhaseCommitFailTest {
    @Autowired
    private TransactionManager transactionManager;

    /**
     * Test situation:
     * 1) An XAResource is enlisted in a running transaction
     * 2) On xa_end the XAResource throws an XAException with code XA_RBROLLBACK
     * 3) On xa_rollback the XAResource throws an XAException with code XAER_NOTA
     * <p>
     * Expected behaviour: RollbackException
     * Actual behaviour: HeuristicMixedException
     * <p>
     * Apparently IBM MQ will return XA_RBROLLBACK on xa_end when the transaction fails
     */
    @Test
    void testOnePhaseCommitRollbackOnXaEnd() throws XAException, SystemException, NotSupportedException, RollbackException {
        XAResource mockXaResource = mock(XAResource.class);
        doThrow(new XAException(XAException.XA_RBROLLBACK)).when(mockXaResource).end(any(), anyInt());
        doThrow(new XAException(XAException.XAER_NOTA)).when(mockXaResource).rollback(any());

        transactionManager.begin();
        transactionManager.getTransaction().enlistResource(mockXaResource);
        transactionManager.setRollbackOnly();
        assertThrows(RollbackException.class, transactionManager::commit);
    }
}
