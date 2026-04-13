# aws-web-dashboard

AWS web dashboard built with HTMX and Scala.  
The UI is using plain Bulma CSS.

## Prerequisites

- docker compose
- jdk 21+
- [deder](https://github.com/sake92/deder) build tool (`brew tap sake92/tap/deder`)

## Run

```shell
# Run floci on port 4566
# you can also use localstack of course
docker compose up -d

# run the web app on port 8181
deder exec -t run -m app
```


## Dev

```shell
deder bsp install
```

and then open in VSCode (with Metals extension) or Intellij (as BSP project).


