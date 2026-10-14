package com.architectpsg.commandlibrary

import org.robolectric.annotation.Config

/** Scoped host OS bridge; the device wrapper uses Android's actual Os implementation. */
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class EnglishCatalogAccessIntegrationTest : EnglishCatalogAccessCases()
