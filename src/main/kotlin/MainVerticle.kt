package main

import graphql.ExecutionInput.newExecutionInput
import graphql.GraphQL
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import main.util.GraphQLRequestDataException
import main.util.parseGraphQLRequest


@Suppress("unused")
class MainVerticle : AbstractVerticle() {

    override fun start(startFuture: Future<Void>) {
        val router = createRouter()

        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(config().getInteger("http.port", 8080)) { result ->
                    if (result.succeeded()) {
                        startFuture.complete()
                    } else {
                        startFuture.fail(result.cause())
                    }
                }
    }


    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create())

        get("/").handler { it.response().end("Test") }

        post("/graphql")
                .handler(initHandler)
                .handler(graphqlHandler)
                .handler(failureHandler)
                .handler(finalHandler)
    }


    private val graphqlHandler = Handler<RoutingContext> { routingContext ->
        val graphqlRequest = parseGraphQLRequest(routingContext)
        if (null === graphqlRequest) {
            throw GraphQLRequestDataException("Invalid GraphQL Request")
            routingContext.next()
        }

        val schema = "type Query{hello: String}"

        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema)

        val runtimeWiring = newRuntimeWiring()
                .type("Query") { builder -> builder.dataFetcher("hello", StaticDataFetcher("world")) }
                .build()

        val schemaGenerator = SchemaGenerator()
        val graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

        val executionInput = newExecutionInput()
        executionInput.query(graphqlRequest!!.query)
        if (null !== graphqlRequest.operationName) executionInput.operationName(graphqlRequest.operationName)
        if (null !== graphqlRequest.variables) executionInput.variables(graphqlRequest.variables)

        val build = GraphQL.newGraphQL(graphQLSchema).build()
        val executionResult = build.execute(executionInput.build())
        routingContext.response().writeJson(executionResult.toSpecification())
        routingContext.next()
    }

    private val initHandler = Handler<RoutingContext> {
        it.response().isChunked = true
        it.next()
    }

    private val finalHandler = Handler<RoutingContext> {
        it.response().end()
    }

    private val failureHandler = Handler<RoutingContext> {
        if (it.failed()) {
            val failure = it.failure()

            if (failure is io.vertx.ext.web.api.validation.ValidationException) {
                it.response().setStatusCode(400).writeJson(object {
                    val error = object {
                        val message = failure.message
                    }
                })
            }

            it.response().writeJson(object {
                val error = object {
                    val message = failure.message
                }
            })

        }
        it.next()
    }
}

fun HttpServerResponse.writeJson(obj: Any) {
    this.putHeader("Content-Type", "application/json; charset=utf-8").write(Json.encodePrettily(obj))
}