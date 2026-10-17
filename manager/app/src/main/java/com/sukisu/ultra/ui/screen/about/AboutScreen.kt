package com.sukisu.ultra.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    // Source code plus the project's QQ group; the Telegram channel the upstream string
    // mentions is not part of this project. The QQ row opens QQ itself, not a browser.
    val htmlString = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com/HyperSU-Team/HyperSU\">GitHub</a></b>",
        "https://qun.qq.com/universal-share/share?ac=1&authKey=8hQdPwdedWVxmI7Oj12BDYlwHUIgehcpvzVycp3bd%2FNRg2zlRbtnJiaTPr%2FX0lNG&busi_data=eyJncm91cENvZGUiOiIxMTIxOTA4NjQxIiwidG9rZW4iOiJlbmVBSnBhWjNnWFRvUm5IbHNsK08vaDVndTBuTGc5Qy9Uazl5aW5nUi9ydXhDQUFzcmd6bVpkb0dUd1NDSVVrIiwidWluIjoiMjQxNTgzMTk0NiJ9&data=an4ho15SknABma67muFTwUOjtktp1ndORbPazI0j-BJaTsapc52ZYjEZpO3sAtz48dJeuiZjioJx7NlAaIqsiw&svctype=4&tempid=h5_group_info",
    )
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        links = extractLinks(htmlString),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = { url -> openAboutLink(context, url) },
    )

    AboutScreenFolk(state, actions)
}
