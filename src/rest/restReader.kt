package rest

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import repo.*

fun Application.restReader(
    readerRepo: Repo<Reader>,
    readerSerializer: KSerializer<Reader>,
    myBookRepo: Repo<MyBook>,
    myBookSerializer: KSerializer<MyBook>,
    myDebtRepo: Repo<MyDebt>,
    myDebtSerializer: KSerializer<MyDebt>
) {
    routing {
        route("/reader") {
            post {
                parseBody(readerSerializer)?.let { reader ->
                    if (readerRepo.all().filter { it.name == reader.name }.isEmpty()) {
                        if (readerRepo.add(reader)) {
                            val user = readerRepo.all().find { it.name == reader.name }!!
                            call.respond(user)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    } else {
                        call.respond(HttpStatusCode.Conflict, "Читатель с таким именем уже существует")
                    }
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
        }
        route("/reader/{id}") {
            put {
                parseBody(readerSerializer)?.let { elem ->
                    parseId()?.let { id ->
                        if (readerRepo.update(id, elem))
                            call.respond(HttpStatusCode.OK)
                        else
                            call.respond(HttpStatusCode.NotFound)
                    }
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
            delete {
                parseId()?.let { id ->
                    if (readerRepo.delete(id))
                        call.respond(HttpStatusCode.OK)
                    else
                        call.respond(HttpStatusCode.NotFound)
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
        }
        route("/reader/myBook") {
            post {
                parseBody(myBookSerializer)?.let { elem ->
                    if (myBookRepo.add(elem))
                        call.respond(HttpStatusCode.OK)
                    else
                        call.respond(HttpStatusCode.NotFound)
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
            get {
                val books = myBookRepo.all()
                if (books.isNotEmpty()) {
                    call.respond(books)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        route("/reader/{id}/myBook") {
            get {
                val books = myBookRepo.all().filter { it.readerId == parseId() }
                if (books.isNotEmpty()) {
                    call.respond(books)
                } else {
                    call.respond(listOf<MyBook>())
                }
            }
        }

        route("/reader/myBook/{id}") {
            get {
                parseId()?.let { id ->
                    myBookRepo.get(id)?.let { elem ->
                        call.respond(elem)
                    } ?: call.respond(HttpStatusCode.NotFound)
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
            put {
                parseBody(myBookSerializer)?.let { elem ->
                    parseId()?.let { id ->
                        if (myBookRepo.update(id, elem))
                            call.respond(HttpStatusCode.OK)
                        else
                            call.respond(HttpStatusCode.NotFound)
                    }
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
            delete {
                parseId()?.let { id ->
                    if (myBookRepo.delete(id))
                        call.respond(HttpStatusCode.OK)
                    else
                        call.respond(HttpStatusCode.NotFound)
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
        }

        route("/reader/{id}/myBook/allMyDebt") {
            get {
                val readerBooks = myBookRepo.all().filter { it.readerId == parseId() }
                val allDebts = myDebtRepo.all()
                val debts = arrayListOf<MyDebt>()
                for (book in readerBooks) {
                    val myDebts = allDebts.filter { it.readerId == book.id }
                    for (debt in myDebts) {
                        debts.add(debt)
                    }
                }
                call.respond(debts)
            }
        }
        route("/reader/{id}/myBook/myDebts") {
            get {
                val debtsArray = myDebtRepo.all()
                val books = myBookRepo.all().map { book ->
                    debtsArray.any {
                        it.myBookId == book.id && it.readerId == parseId()
                    }
                }
                call.respond(books)
            }
        }

        route("/reader/myBook/myDebts") {
            get {
                if (myDebtRepo.all().isNotEmpty()) {
                    val books = myBookRepo.all()
                    call.respond(books)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

fun PipelineContext<Unit, ApplicationCall>.parseId(id: String = "id") =
    call.parameters[id]?.toIntOrNull()

fun PipelineContext<Unit, ApplicationCall>.myBookId(id: String = "myBookId") =
        call.parameters[id]?.toIntOrNull()

suspend fun <T> PipelineContext<Unit, ApplicationCall>.parseBody(
    serializer: KSerializer<T>
) =
    try {
        Json.decodeFromString(
            serializer,
            call.receive()
        )
    } catch (e: Throwable) {
        null
    }
