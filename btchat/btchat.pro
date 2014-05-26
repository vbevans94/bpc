TEMPLATE = app
TARGET = btchat

QT = core bluetooth widgets

SOURCES = \
    main.cpp \
    chatserver.cpp

HEADERS = \
    chatserver.h

FORMS =

target.path = $$[QT_INSTALL_EXAMPLES]/bluetooth/btchat
INSTALLS += target
