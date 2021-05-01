package fr.janalyse.cem

//import caliban.client.Operations.RootQuery
//import caliban.client.SelectionBuilder
//import fr.janalyse.cem.model._
//import fr.janalyse.cem.tools.DescriptionTools
//import sttp.client3._
//import sttp.client3.asynchttpclient.zio.{SttpClient, _}
//import sttp.model.Uri
//import zio.logging._
//import zio.{RIO, ZIO}

/*
TO BE CONTINUED, ONCE GITHUB GraphQL is feature complete
and Caliban give ways to generate graphql sub piece of codes
 */

//object RemoteGitlabGraphqlOperations {
//
//  def gitlabInjectAuthToken[A,B](request:Request[A,B], tokenOption: Option[String]) = {
//    val base = request.header("Content-Type","application/json")
//    tokenOption.fold(base)(token => base.header("Authorization", s"Bearer $token"))
//  }
//
//  case class RemoteSnippet(id: String, description: Option[String], url: String, filename: Option[String])
//
//  case class RemoteSnippetConnection(snippets: List[RemoteSnippet], pagination: RemoteSnippetPagination)
//
//  case class RemoteSnippetPagination(endCursor: Option[String], hasNextPage: Boolean)
//
//  def gitlabRemoteGistsToRemoteExampleState(snippets: List[RemoteSnippet]): List[RemoteExampleState] = {
//    for {
//      snippet <- snippets
//      desc <- snippet.description
//      (uuid, checksum) <- DescriptionTools.extractMetaDataFromDescription(desc)
//      url = snippet.url
//      filename = snippet.filename
//    } yield RemoteExampleState(
//      remoteId = snippet.id,
//      description = desc,
//      url = url,
//      filename = filename,
//      uuid = uuid,
//      checksum = checksum,
//    )
//  }
//
//  def snippetQuery(after: Option[String] = None, first: Option[Int] = Some(100)): SelectionBuilder[RootQuery, Option[Option[RemoteSnippetConnection]]] = {
//    import fr.janalyse.cem.graphql.gitlab.Client.PageInfo._
//    import fr.janalyse.cem.graphql.gitlab.Client.Query._
//    import fr.janalyse.cem.graphql.gitlab.Client.SnippetConnection._
//    import fr.janalyse.cem.graphql.gitlab.Client.User.snippets
//    import fr.janalyse.cem.graphql.gitlab.Client._
//
//    currentUser(
//      snippets(first=first, after = after)(
//        (
//          nodes((Snippet.id ~ Snippet.description ~ Snippet.webUrl ~ Snippet.fileName).mapN(RemoteSnippet)) ~
//            pageInfo((endCursor ~ hasNextPage).mapN(RemoteSnippetPagination))
//          ).map { case (Some(a), c) => RemoteSnippetConnection(a.flatten, c) }
//      )
//    )
//  }
//
//  def snippetAddMutation(description:String) = {
//    import fr.janalyse.cem.graphql.gitlab.Client.Mutation._
//    import fr.janalyse.cem.graphql.gitlab.Client._
//    val input = CreateSnippetInput(
//      title = description,
//      visibilityLevel = VisibilityLevelsEnum.public
//    )
//    createSnippet(input)(
//      Snippet.id ~ Snippet.webUrl
//    )
//  }
//
//  def gitlabRemoteExamplesStatesFetch(adapterConfig: PublishAdapterConfig, after: Option[String] = None): RIO[Logging with SttpClient, List[RemoteExampleState]] = {
//    val uriEither = Uri.parse(adapterConfig.graphqlEndPoint).swap.map(msg => new Error(msg)).swap
//    for {
//      apiURI <- RIO.fromEither(uriEither)
//      query = snippetQuery(after).toRequest(apiURI, useVariables = true)
//      responseOptionOption <- send(gitlabInjectAuthToken(query, adapterConfig.token)).map(_.body).absolve
//      responseOption <- RIO.getOrFail(responseOptionOption) // TODO refactor
//      response <- RIO.getOrFail(responseOption) // TODO refactor
//      nextResults <-
//        if (response.pagination.hasNextPage) gitlabRemoteExamplesStatesFetch(adapterConfig, response.pagination.endCursor)
//        else RIO.succeed(List.empty[RemoteExampleState])
//    } yield gitlabRemoteGistsToRemoteExampleState(response.snippets) ::: nextResults
//  }
//
//  def gitlabRemoteExampleAdd(adapterConfig:PublishAdapterConfig, addExample:AddExample):ZIO[Logging with SttpClient, Option[Throwable],RemoteExample] = {
//    ???
//  }
//
//  def gitlabRemoteExampleUpdate(adapterConfig:PublishAdapterConfig, update:UpdateRemoteExample):ZIO[Logging with SttpClient, Option[Throwable],RemoteExample] = {
//    ???
//  }
//
//  def gitlabRemoteExampleChangesApply(adapterConfig: PublishAdapterConfig)(todo: WhatToDo) : ZIO[Logging with SttpClient, Option[Throwable],Option[RemoteExample]] = {
//    todo match {
//      case _:IgnoreExample => ZIO.succeed(None)
//      case _:UnsupportedOperation => ZIO.succeed(None)
//      case _:OrphanRemoteExample => ZIO.succeed(None)
//      case KeepRemoteExample(uuid, example, state) => ZIO.succeed(Some(RemoteExample(example, state)))
//      case exampleTODO:UpdateRemoteExample => gitlabRemoteExampleUpdate(adapterConfig, exampleTODO).asSome
//      case exampleTODO:AddExample => gitlabRemoteExampleAdd(adapterConfig, exampleTODO).asSome
//    }
//  }
//
//  def gitlabRemoteExamplesChangesApply(adapterConfig: PublishAdapterConfig, todos: Iterable[WhatToDo]): RIO[Logging with SttpClient, Iterable[RemoteExample]] = {
//    for {
//      remotes <- RIO.collect(todos)(gitlabRemoteExampleChangesApply(adapterConfig)).map(_.flatten)
//    } yield remotes
//  }
//
//}
