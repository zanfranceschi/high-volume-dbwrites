import argonaut.Argonaut.casecodec3
import argonaut.CodecJson
import scalikejdbc._
import argonaut._
import Argonaut._


case class Thing(id: String, name: String, status: String) {
	def this(id: String, name: String) = this(id, name, "created")
}


object Thing extends SQLSyntaxSupport[Thing] {
	
	implicit def ThingCodecJson: CodecJson[Thing] = casecodec3(Thing.apply, Thing.unapply)("id", "name", "status")
	
	def apply(t: ResultName[Thing])(rs: WrappedResultSet): Thing = {
		new Thing(id = rs.get(t.id), name = rs.get(t.name), status = rs.get(t.status))
	}
}