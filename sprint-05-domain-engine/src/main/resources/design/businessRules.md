buy succeeds and updates cash and position -> 5 (4.1.1)

buy rejected on insufficient funds -> 6 (4.1.2) 

(split as buy and sell here) 4

sell rejected on insufficient holdings -> 7 (4.2) 

order rejected on an account that does not exist -> 2 account

order rejected on an inactive account -> 1 account

order rejected on an unknown or non-tradable instrument -> 3 instrument

duplicate idempotency key rejected -> 0 

a request breaking two rules returns the code of the first

nforce existence, then active status, then a tradable instrument, then quantity, then price, then affordability on a buy, then held quantity on a sell, then the idempotency key.
1. existence ACC-404
2. active ACC-403
3. instrument INS-404
4. quantity VAL-400
5. price VAL-400
Buy: 
6. enough funds ACC-400

Sell: 
7. Enough holdings ACC-410

8. Idempotency -> ACC-409 

checked last cost the previous errors are more important

Unit Test Execution Paths
buy succeeds and updates cash and position

buy rejected on insufficient funds

sell rejected on insufficient holdings

order rejected on an account that does not exist

order rejected on an inactive account

order rejected on an unknown or non-tradable instrument

duplicate idempotency key rejected

a request breaking two rules returns the code of the first

Enforce existence, then active status, then a tradable instrument, then quantity, then price, then affordability on a buy, then held quantity on a sell, then the idempotency key.

Check quantity and price in the rules as well as on the DTO, because the domain has to hold for a caller that never ran a validator.

Leave room for the two rules that carry no code this sprint: cash and position move together or neither moves, and every order is recorded, including a rejected one.

Design a seam that makes the idempotency rule expressible and testable here without a database, since in Sprint 6 the authority is the unique constraint you built in Sprint 3.

