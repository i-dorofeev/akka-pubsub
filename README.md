[![Build Status](https://travis-ci.org/i-dorofeev/akka-pubsub.svg?branch=master)](https://travis-ci.org/i-dorofeev/akka-pubsub)
# akka-pubsub

This is a prototype project for an event broker backed up by akka.

The key feature of this broker is guaranteed send and delivery with exactly-once semantics.

## Motivation
Currently, I am working on a monolithic enterprise application and I would like to make use
of some modern techniques to software development such as microservices and reactive principles.
Although a lot of various toolkits are available to tackle the intricacies of these approaches,
the majority of them may require a great redesign of the whole application which isn't feasible
for the time being.

The first step to take is to decouple different services from each other by leveraging the
publish/subscribe design pattern. While implementation of this pattern is rather straightforward
in a monolithic application, it may be challenging to implement it in a microservices environment.
The major requirement is to provide reliable event delivery with "exactly-one" semantics so it is
possible to concentrate on domain logic leaving all the necessary plumbing to a specialized tool.
