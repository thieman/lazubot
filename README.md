# lazubot

A Zulip bot that runs Clojure code. Currently stalking around Hacker School.

## Deploying

Lazubot can be deployed using [Docker](http://www.docker.io/).

```
git clone git@github.com:tthieman/lazubot
docker build -t lazubot - < lazubot/MasterDockerfile
docker run lazubot
```

## License

Copyright © 2013 Travis Thieman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
