# Test Case for behaviour of Narayana when performing a 1PC on IBM MQ where xa_end fails with XA_RBROLLBACK

Apparently IBM MQ may respond to xa_end with an XA_RBROLLBACK. This happens when for instance a local queue is full. When sending a JMS Message to an alias queue transactionally, the xa_end with flag TM_SUCCESS fails. IBM MQ probably only then finds out that the transactional work cannot be performed and responds to xa_end with an XA_RBROLLBACK. MQ also rolls back the transaction, because when sending an xa_rollback later, MQ responds with an XAER_NOTA.

This seems to be related to the warning that is no longer being logged in the situaties where the flag for xa_end was TM_FAIL: [JBTM-3559](https://issues.redhat.com/browse/JBTM-3559)
This ticket may very well be closed, since it was fixed with the following: [JBTM-3345](https://issues.redhat.com/browse/JBTM-3345)

When performing a two phase commit, this resulting XAER_NOTA results in a RollbackException. But when performing a one phase commit (through onePhaseCommit-optimization), this resulting XAER_NOTA results in a HeuristicMixedException and a related entry in the objectstore.
* 1PC HeuristicMixedException from HEURISTIC_HAZARD from XAResourceRecord.topLevelOnePhaseCommit. These comments are in XAResourceRecord at this point where the HEURISTIC_HAZARD is returned:
```java
// something committed or rolled back without asking us!
// Some RMs do (or did) one-phase commit but interpreting end as prepare and once you’ve prepared (in end) you can commit or rollback when a timeout goes off
// I *think* we’re talking about a while ago so those RMs may no longer exist.
// The alternative implication is that the RM timed out the branch between the end above and the completion call, if we do make a change to assume that scenario
// it is possible we could break existing deployments so changes should be considered and potentially configurable
```
* 2PC RollbackException from PREPARE_NOTOK from XAResourceRecord.topLevelPrepare. These comments are in XAResourceRecord at this point where the PREPARE_NOTOK is returned:
```java
// resource may have arbitrarily rolled back (shouldn't, but ...)
// will not call rollback
```

For now we are explicitly turning off onePhaseCommit-optimization, according to [this discussion](https://developer.jboss.org/thread/145289) that also refers to this [ticket](https://issues.redhat.com/browse/JBTM-278). I also found the [following information](https://lists.apache.org/thread/nsnogpl8nrgf4tqvfrlzdclsfp2h5bjl):

> 9) When Rollback-only is set on the transaction branch the XA
> Specification explains that the Resource Manager can unilaterally roll
> back and forget a transaction branch any time before it prepares it:
> "An RM can also unilaterally roll back and forget a transaction branch
> any time before it prepares it. A TM detects this when an RM
> subsequently indicates that it does not recognise the XID".
> .
> 10) This is what MQ does after returning the XA_RBROLLBACK errorcode
> '100' on XA_End, it forgets the transaction, which is why when the
> transaction manager makes the XA_Rollback call MQ returns the errorCode
> '-4' which is "XAER_NOTA" indicating that it is not aware of the XID for
> which rollback has been called on. This is the correct and expected
> behaviour and we would expect the Transaction Manager to handle this
> appropriately.
