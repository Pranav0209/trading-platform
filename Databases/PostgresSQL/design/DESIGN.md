# Historical trade data design 

## Description (Requirement)

Acceptance Criteria
DESIGN.md covers what is retained about an executed trade beyond what the order carries, at what grain, how it is populated and by which component.

It covers how the Sprint 7 extract pulls incrementally rather than scanning the whole history, how the design behaves at a hundred times the volume, and what it costs on write and in operational complexity.

Tasks
Write two pages in DESIGN.md covering retention grain, population, incremental extraction, growth and cost.

State a position on partitioning, archival and retention, or make a defensible argument that none of it is needed yet.

Notes
Reduced for this cohort to a documented design rather than a built implementation. It is discussed at the design review, and a decision you can defend beats a diagram nobody can explain.


_____

## Our idea - 

We track all the trade (or transactions or orders) data in the orders table. 
We do not store the same data redundantly more than twice. 

But we do hold a PnL table where we track all the Profit and Loss whenever a 'SELL' happens, to record the historical trend of P/L for that particular person 

The holdings table will compile the orders executed and store the average values from the orders table. 
the trigger happens whenever something is entered inside the orders table. 

So, in the orders table - we populate - 
```
order id (PK)
acc id, inst id (FK)
type, side
quantity, limit price, brokerage, idempotency
placed at, updated at, completed at, cancelled at
```

By cost, the write operation has O(1) cost. 
there is no updates in the table as such.
We have a status column where we mark a trade as pending or completed. 

We can further modify the tables whenever required if we want to push the completed orders to a new table immediately. (If the index won't suffice - because index will be maximum O(log n) and not O(1) while this new table for completed orders will be O(1))

PnL is mainly for tracking history of Profit and Loss. 
_______