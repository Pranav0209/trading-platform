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
4. quantity ORD-400
5. price ORD-400
Buy: 
6. enough funds ACC-

Sell
7. Enough holdings

8. Idempotency -> checked last cost the previous errors are more important