package workdocs

import com.amazonaws.services.workdocs.AmazonWorkDocsClientBuilder
import com.amazonaws.services.workdocs.model.*

object WorkDocs {
  var region : String? = null
  var profile: String? = null
  val client by lazy {
    AmazonWorkDocsClientBuilder.standard().also {
      if (region != null)
        it.region = region

    }.build()
  }
}
fun listFolderContents( name: String , parentFolderID : String , type : String  ) =
 listFolderContents( WorkDocName(name , parentFolderID)  , type )
fun listFolderContents( name: WorkDocName , type : String  ) = listFolderContents( getFolderId(name), type )
fun listFolderContents( folderId: String , type : String = FolderContentType.ALL.name ) : Sequence<DescribeFolderContentsResult> {
  fun request(marker : String?) = DescribeFolderContentsRequest().apply {
    this.folderId = folderId
    this.type = type
    this.marker = marker
  }
  return generateSequence<DescribeFolderContentsResult>({
    WorkDocs.client.describeFolderContents(request(null))
  }) {prev->
    when {
      prev.marker.isNullOrBlank() -> null
      else                        -> WorkDocs.client.describeFolderContents(request(prev.marker))
    }
  }
}
fun listDocuments( name: String , parentFolderID : String )  = listDocuments( getFolderId(name,parentFolderID))
fun listDocuments( folderId: String ) =
  listFolderContents( folderId , FolderContentType.DOCUMENT.name ).flatMap { it.documents.asSequence().map {WorkDocFile(it) } }
fun listFolders( name: WorkDocName) = listFolders(getFolderId(name))

fun listFolders(  parentFolderID : String ) : Sequence<WorkDocFolder> =
  listFolderContents( parentFolderID, FolderContentType.FOLDER.name ).flatMap { it.folders.asSequence().map {WorkDocFolder(it) } }
fun listAll(  parentFolderID : String ) : Sequence<WorkDoc> =
  listFolderContents( parentFolderID , FolderContentType.ALL.name ).flatMap {
    sequenceOf( it.folders.asSequence().map {WorkDocFolder(it) } ,
                it.documents.asSequence().map {WorkDocFile(it) } ).flatMap { it } }



fun getFolderId( name: WorkDocName ) = getFolderId(name.name , name.parentFolderID )
fun getFolderId( name: String, parentFolderID : String ) = requireNotNull(findFolder( name , parentFolderID )?.id)

fun findFolder(name: String , parentFolderID : String) = listFolders(parentFolderID).firstOrNull { it.name.name == name }

fun findDocument(name : String, parentFolderID : String) = listDocuments(parentFolderID).firstOrNull { it.name.name == name }
fun findDocument(name : WorkDocName ) = findDocument(name.name, name.parentFolderID)
fun getDocument(id : String ) = requireNotNull(WorkDocs.client.getDocument(GetDocumentRequest().apply {documentId = id }))
fun getFolder( id: String ) = requireNotNull(WorkDocs.client.getFolder(GetFolderRequest().withFolderId(id))).let { WorkDocFolder(it.metadata, it.customMetadata) }
fun findFolder(name : WorkDocName ) = findFolder(name.name, name.parentFolderID)
fun findWorkDoc( name: String , parentFolderID : String ) : WorkDoc  =
  listFolderContents(parentFolderID, FolderContentType.ALL.name).also {
    println( "listFolderContents: ${name} ${parentFolderID}")
  }.map {

    it.documents.firstOrNull {it.latestVersionMetadata.name == name}?.let { WorkDocFile(it) } ?:
     it.folders.firstOrNull {it.name == name}?.let { WorkDocFolder(it) }
  }.filterNotNull().first()

fun findWorkDoc( name: WorkDocName ) = findWorkDoc(name.name,name.parentFolderID)


data class WorkDocName(
  val name : String,
  val parentFolderID : String) {
  override fun toString() : String = name
}
interface WorkDoc   {
    val name : WorkDocName
    val id : String
    val customMetadata : Map<String,String>?
    val isFolder : Boolean
    val isFile : Boolean get() = ! isFolder
    val folderMetadata : FolderMetadata? get() = null
    val fileMetadata   : DocumentMetadata? get() = null
}


interface WorkDocFile : WorkDoc  {
  override val isFolder : Boolean
    get() =  false
  val metadata : DocumentMetadata get() = fileMetadata!!

}

interface WorkDocFolder : WorkDoc {
  override val isFolder : Boolean
    get() =  true
  val metadata get() = folderMetadata!!

}

fun WorkDocFile( id: String ) = object : WorkDocFile {
  val result by lazy { getDocument(id) }
  override val id = id
  override val customMetadata : Map<String, String>
    get() = result.customMetadata
  override val fileMetadata get() = result.metadata
  override val name by lazy {
    WorkDocName(metadata.latestVersionMetadata.name, metadata.parentFolderId)
  }
}
fun WorkDocFile( metadata : DocumentMetadata , customMetadata : Map<String, String> ) = object : WorkDocFile {
  override val id
    get() = metadata.id
  override val metadata  = metadata
  override val customMetadata = customMetadata
  override val name
    get() = WorkDocName(metadata.latestVersionMetadata.name, metadata.parentFolderId )
}
fun WorkDocFile( metadata : DocumentMetadata ) = object : WorkDocFile {
  override val id
    get() = metadata.id
  override val metadata  = metadata
  override val customMetadata by lazy { getDocument(id). customMetadata }
  override val name
    get() = WorkDocName(metadata.latestVersionMetadata.name, metadata.parentFolderId )
}

fun WorkDocFile( documentName: WorkDocName ) = object : WorkDocFile {
  private val result by lazy { requireNotNull(findDocument(documentName)) }
  override val id get() = result.id
  override val metadata : DocumentMetadata  get() = result.metadata
  override val name = documentName
  override val customMetadata by lazy { getDocument(id).customMetadata }
 }
fun WorkDocFolder( id: String ) = object : WorkDocFolder {
  private val result by lazy { requireNotNull(getFolder(id))}
  override val customMetadata get() = result.customMetadata
  override val metadata get() = result.metadata
  override val name by lazy { WorkDocName(metadata.name, metadata.parentFolderId ) }
  override val id = id
}
fun WorkDocFolder( metadata : FolderMetadata , customMetadata : Map<String,String>? = null  )  = object : WorkDocFolder {
  override val id = metadata.id
  override val metadata  = metadata
  override val customMetadata = customMetadata
  override val name = WorkDocName(metadata.name, metadata.parentFolderId )
}
fun WorkDocFolder( metadata : FolderMetadata)  = object : WorkDocFolder {
  override val id = metadata.id
  override val metadata  = metadata
  override val customMetadata by lazy { getFolder(id).customMetadata }
  override val name = WorkDocName(metadata.name, metadata.parentFolderId )
}
