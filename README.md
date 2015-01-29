# modular cylon (OAuth2) example

This is a [juxt/modular](https://github.com/juxt/modular) and [juxt/cylon](https://github.com/juxt/cylon) (Oauth2) example integration

You can read more [http://tangrammer.github.io](http://tangrammer.github.io/posts/13-01-2015-using-cylon-oauth2.html)

## Usage

To run the application, install [Leiningen](http://leiningen.org/) and type

```
lein run
```

## Deploy GAE

```clojure
lein clean
lein ring uberwar
unzip target/cylon-oauth2-demo-0.1.0-SNAPSHOT-standalone.war -d target/war
cp appengine-web.xml target/war/WEB-INF/
appcfg.sh --oauth2 update target/war/

```

```clojure

(def refresh-token "1/DGLSKAIlChe1jPJB1dCGpE2BhX-a_EOr850Vhx3jnxg")
(apply client/refresh-access-token ((juxt :authorize-uri :client-id :client-secret ) (-> system :webapp-oauth-client) ))

```

## Development

For rapid development, use the REPL from the command line or via your
favorite code editor.

```
lein repl
user> (dev)
dev> (go)
```

**Visit your app**   
[http://localhost:8010](http://localhost:8010)

After making code changes, reset your application's state (causing all
your modifications to be reloaded too).

```
dev> (reset)
```

To add a test user `[:uid "tangrammer" :password "clojure"]` each time you reset your app

```
dev> (reset+data)
```

Rinse and repeat.



## Copyright and License

The MIT License (MIT)

Copyright © 2014 [Juan A. Ruz](https://github.com/tangrammer) (juxt.pro)

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
