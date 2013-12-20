# lazubot

A Zulip bot that runs arbitrary, un-sandboxed Clojure code. Currently stalking around Hacker School.

## Deploying

Lazubot can be deployed using [Docker](http://www.docker.io/).

```
git clone git@github.com:tthieman/lazubot
lazubot/start_lazubot  # see this script for the full Docker commands
```

## Architecture

<img height=300 src="http://i.imgur.com/LYlByWY.png"></img>

Lazubot itself is deployed with Docker. The main Lazubot instance is referred to as the "master" and is responsible for connecting to Zulip and responding to any messages it receives. This runs on top of your actual machine, the "host."

Lazubot executes any Clojure code you throw at it with no restrictions at all. To allow this with a modicum of sanity, the master spins off child Docker containers to execute the Clojure code. These containers are modeled off of [Docker in Docker](https://github.com/jpetazzo/dind) and are fully expendable. They are cut off from the Internet and are only allowed to communicate on port 8080, which they use to talk to the master container using [ZeroMQ](https://github.com/lynaghk/zmq-async). If anything causes an executor to time out when executing Clojure code, the master will kill it and spin up a new container to replace it.

## License

Copyright © 2013 Travis Thieman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
