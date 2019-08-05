package workdocs

import org.apache.tika.Tika
import java.io.File
import java.util.*

val tika by lazy { Tika() }

fun contentTypeFor( file: File , fast : Boolean = false ) = when(fast) {
  false -> tika.detect(file)
  true  -> tika.detect(file.absolutePath)
}

val File.modtime get() = Date(lastModified())
val File.ctime get() = Date(lastModified())
