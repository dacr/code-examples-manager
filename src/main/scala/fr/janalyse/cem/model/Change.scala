package fr.janalyse.cem.model

enum WhatToDo {
  case IgnoreExample(example: CodeExample)
  case OrphanRemoteExample(uuid: String, state: RemoteExampleState)
  case DeleteRemoteExample(uuid: String, state: RemoteExampleState)
  case AddExample(uuid: String, example: CodeExample)
  case KeepRemoteExample(uuid: String, example: CodeExample, state: RemoteExampleState)
  case UpdateRemoteExample(uuid: String, example: CodeExample, state: RemoteExampleState)
  case UnsupportedOperation(uuidOption: Option[String], exampleOption: Option[CodeExample], stateOption: Option[RemoteExampleState])
}
