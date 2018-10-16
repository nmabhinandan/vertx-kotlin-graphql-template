package main.util

import com.squareup.moshi.*
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import java.io.IOException

private val logger = KotlinLogging.logger {}

@JsonClass(generateAdapter = true)
class GraphqlRequest(val query: String, val operationName: String?, val variables: Map<String, Any>?)

class GraphQLRequestDataException(override val message: String?) : Exception(message)

val moshi = Moshi.Builder().build()!!
val graphQLRequestAdapter: JsonAdapter<GraphqlRequest> = moshi.adapter(GraphqlRequest::class.java)

val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)!!
val variablesTypeAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(mapType)

fun parseGraphQLRequest(context: RoutingContext): GraphqlRequest? {

    var graphQLRequest: GraphqlRequest? = null

    try {

        if (context.request().method() == HttpMethod.POST) {
            graphQLRequest = graphQLRequestAdapter.fromJson(context.bodyAsString)
        } else if (context.request().method() == HttpMethod.GET) {

            var query: String? = null
            with(context.queryParam("query").toString()) {

                if (null !== this || "" != this) {
                    query = this
                } else {
                    throw GraphQLRequestDataException("Failed to parse graphQL query from GET request")
                }
            }

            var opreationName: String? = null
            with(context.queryParam("opreationName").toString()) {
                if (null !== this && "" != this) {
                    opreationName = this
                }
            }


            var variables: Map<String, Any>? = null
            with(context.queryParam("variables").toString()) {
                if (null !== this && "" != this) {
                    variables = variablesTypeAdapter.fromJson(this)
                }
            }

            graphQLRequest = GraphqlRequest(
                    query = query!!,
                    operationName = opreationName,
                    variables = variables
            )
        }
    } catch (exception: IOException) {
        logger.error(exception) { "parseJsonRequest: Error while parsing graphQL request. ${exception.message}" }
    } catch (exception: JsonDataException) {
        logger.error(exception) { "parseJsonRequest: Error while parsing graphQL request. ${exception.message}" }
    } catch (exception: GraphQLRequestDataException) {
        logger.error(exception) { "parseJsonRequest: Error while parsing graphQL request. ${exception.message}" }
    } finally {
        return graphQLRequest
    }
}

