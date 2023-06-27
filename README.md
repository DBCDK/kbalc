# kbalc
Simple/stupid kafka logdir balancer

## What do?

It moves replicas on a specified broker, one at a time, from a most-populated logDir to a least-populated logDir.

## How do?

```
nix run . --broker BROKER_ID --server SOME_KAFKA_HOST
```

## Why do?

We did not find a very simple tool for balancing logDirs on a broker (after, say, adding a JBOD disk) -- everything we found required either full reassignment plans or entire agents to provide metrics to aid decision-making of the tool. We just needed something that "more or less fills the disk, ish" and this is more or less it, ish.

## Beware!

- [#2](https://github.com/DBCDK/kbalc/issues/2) Occasionally crashes logdirs, requires a broker restart.
