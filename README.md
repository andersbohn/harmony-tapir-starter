## Quick start

1. Spin up `Main` with debugger
2. see swagger docs at http://localhost:8080/docs/#/default/getBooksListAll
3. asking for parameter `name` which is `array` of `string` `->` `Option[String[String[Author]]` 
4. which correctly allows all of eg  
```
curl -v "http://localhost:8080/books/list/all"
curl -v "http://localhost:8080/books/list/all?name=fi"
curl -v "http://localhost:8080/books/list/all?name=fi,fy"
curl -v "http://localhost:8080/books/list/all?name=fi,fy&name=boo"
```
5. Note: no actual logic impl, but can check the incoming typed values are correct using a breakpoint or so
6. also the yaml for the query-name is ~ 
```
 /books/list/all:
    get:
      operationId: getBooksListAll
      parameters:
      - name: name
        in: query
        required: false
        schema:
          type: array
          items:
            type: string
```
7. which could be read as ~ `Option[List[List[String]]]` :-)
