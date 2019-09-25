# Graph simplification using the Ramer-Douglas-Peuker algorithm

This repository contains a [Clojurescript](https://clojurescript.org/) webapp to evaluate how the
[Ramer-Douglas-Peuker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) can be used to reduce the amount of data which needs to be 
send to the browser in an web application to display a simple graph.

This application works, but is still unfinished. Missing are

* Performance improvements
* Nicer charts and overall layout
* Working production build. The paths/names of the build artifacts still need to be fixed.

To build the software you need to install

* the Clojure [Deps and CLI](https://clojure.org/guides/deps_and_cli) tools.

After that

    clojure -A:serve-dev

starts a [figwheel](https://figwheel.org/) development server with hot- reloading. Changes to output rendered by react will need a reload of the page to be visible

To run `cljfmt` use

    clojure -A:lint/fix

For more build-targets see the `deps.edn` file.