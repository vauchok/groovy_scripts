@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.6')

import groovyx.net.http.*
import java.util.regex.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

JENKINS_HOME = "/opt/jenkins/master/"
repo_name = "project-releases"
artifact_name = args[0]


def artifact_pattern = "([aA-zZ]+)"
def version_pattern = "(\\d+)"

def function(pattern, artifact_name) {
    Pattern pat=Pattern.compile(pattern)
    Matcher matcher=pat.matcher(artifact_name)
    if (matcher.find()) {
        value = matcher.group()
    }
    return value
}

artifactID = function(artifact_pattern, artifact_name)
groupID = artifactID
version = function(version_pattern, artifact_name)

switch (args[1]) {
    case 'pull':
        def http = new HTTPBuilder("http://nexus/service/siesta/rest/beta/search?repository=${repo_name}&group=${groupID}&version=${version}")
        http.headers['Authorization'] = "Basic " + "nexus-service-user:123456".getBytes('iso-8859-1').encodeBase64()
        http.request(Method.GET) {
            response.success = { resp, json ->
                download_url = json.items.assets.downloadUrl*.getAt(0)[0]
                println("id: ${json.items.name}")
                println("group: ${json.items.group}")
                println("version: ${json.items.version}")
            }
            response.failure = { resp, json ->
                println("Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}")
            }
        }

        def download = new HTTPBuilder(download_url)
        download.headers['Authorization'] = "Basic " + "nexus-service-user:123456".getBytes('iso-8859-1').encodeBase64()
        download.request(Method.GET, ContentType.BINARY) {
            response.success = { resp, binary ->
                new File("${JENKINS_HOME}/workspace/rep/${artifact_name}") << binary.bytes
                println("Successfully downloaded")
            }
            response.failure = { resp, json ->
                println("Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}")
            }
        }
        break
    case 'push':
        def upload = new HTTPBuilder ("http://nexus/repository/${repo_name}/${groupID}/${artifactID}/${version}/${artifact_name}")
        upload.headers[ 'Authorization' ] = "Basic " + "nexus-service-user:123456".getBytes('iso-8859-1').encodeBase64()
        upload.request(Method.PUT, ContentType.BINARY) {
            body = new File ("${JENKINS_HOME}/workspace/rep/${artifact_name}").bytes
            response.success = { resp, data ->
                println("Successfully uploaded")
                println("artifactID: ${artifactID}")
                println("groupID: ${groupID}")
                println("version: ${version}")
            }
            response.failure = { resp, json ->
                println("Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}")
            }
        }
        break
    default: println("Something else")
}
