# Run the pure JVM import pipeline even when unrelated Android UI code cannot compile.
let cache = $env.USERPROFILE | path join .gradle caches modules-2 files-2.1
let jars = glob (($cache | str replace --all '\' '/') + '/**/*.jar') | where {|p| $p !~ '-sources.jar$|-javadoc.jar$'}
def dependency [jars: list<string>, name: string] {
    $jars | where {|p| ($p | path basename) == $name} | first
}
let compiler = dependency $jars 'kotlin-compiler-embeddable-2.2.10.jar'
let stdlib = $jars | where {|p| ($p | path basename) =~ '^kotlin-stdlib-2\..*\.jar$'} | sort | last
let runtime = dependency $jars 'kotlin-script-runtime-2.2.10.jar'
let reflect = dependency $jars 'kotlin-reflect-2.2.10.jar'
let coroutines = dependency $jars 'kotlinx-coroutines-core-jvm-1.8.0.jar'
let annotations = $jars | where {|p| ($p | str replace --all '\' '/') =~ '/org.jetbrains/annotations/' } | first
let jsoup = $jars | where {|p| ($p | path basename) =~ '^jsoup-[0-9].*\.jar$'} | first
let junit = dependency $jars 'junit-4.13.2.jar'
let hamcrest = dependency $jars 'hamcrest-core-1.3.jar'
let classpath = [$stdlib $annotations $jsoup $junit $hamcrest] | str join ';'
let compilerClasspath = [$compiler $stdlib $runtime $reflect $coroutines $annotations] | str join ';'
let java = 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
let output = 'build/jwxt-regression'
mkdir $output
let sources = [
    app/src/main/java/com/example/njupter/data/import/JwxtParser.kt
    app/src/main/java/com/example/njupter/data/CourseInfo.kt
    app/src/main/java/com/example/njupter/data/CourseSession.kt
    app/src/main/java/com/example/njupter/domain/import/TimetableImportMatcher.kt
    app/src/test/java/com/example/njupter/data/import/JwxtParserTest.kt
    app/src/test/java/com/example/njupter/data/import/JwxtImportRegressionTest.kt
    app/src/test/java/com/example/njupter/domain/import/TimetableImportMatcherTest.kt
]
^$java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 11 -classpath $classpath -d $output ...$sources
if $env.LAST_EXIT_CODE != 0 { exit $env.LAST_EXIT_CODE }
^$java -cp ($classpath + ';' + $output + ';app/src/test/resources') org.junit.runner.JUnitCore com.example.njupter.data.import.JwxtParserTest com.example.njupter.data.import.JwxtImportRegressionTest com.example.njupter.domain.import.TimetableImportMatcherTest
exit $env.LAST_EXIT_CODE
