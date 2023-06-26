# kbalc
Simple/stupid kafka logdir balancer

## What do?

It moves replicas on a specified broker, one at a time, from a most-populated logDir to a least-populated logDir.

## How do?

```
nix run . --broker BROKER_ID --server SOME_KAFKA_HOST
```

## Beware!

- [#2](https://github.com/DBCDK/kbalc/issues/2) Occasionally crashes logdirs, requires a broker restart.
