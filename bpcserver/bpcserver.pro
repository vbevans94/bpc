TEMPLATE = app
TARGET = bpcserver

QT = core bluetooth widgets

SOURCES = \
    main.cpp \
    bpcserver.cpp

HEADERS = \
    bpcserver.h

FORMS =

target.path = build/bpcserver
INSTALLS += target
