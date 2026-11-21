cmake_minimum_required(VERSION 3.22.1)
project(WinDroid)

add_compile_definitions(WINDROID_APPLICATION_ID="${WINDROID_APPLICATION_ID}")

add_subdirectory(winlator)
add_subdirectory(vortekrenderer)
add_subdirectory(virglrenderer)
add_subdirectory(midihandler)
add_subdirectory(libadrenotools)
add_subdirectory(gladiorenderer)
