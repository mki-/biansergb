package com.juzhen.ambientlight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.BuildConfig
import com.juzhen.ambientlight.ui.components.Divider
import com.juzhen.ambientlight.ui.components.SectionTitle
import com.juzhen.ambientlight.ui.components.StatRow

@Composable
fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "氛围灯控制程序",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider(Modifier.padding(vertical = 8.dp))

        SectionTitle("连接方式")
        Text(
            text = "低功耗蓝牙（BLE），服务 FFB0，写入特征 FFB1，通知特征 FFB2。",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))
        SectionTitle("协议")
        Text(
            text = "帧格式 55 | L | CMD | PAYLOAD | CK | AA，校验和为从 L 到 PAYLOAD 求和取反的补码低字节。",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))
        SectionTitle("构建信息")
        StatRow("applicationId", BuildConfig.APPLICATION_ID)
        StatRow("版本名", BuildConfig.VERSION_NAME)
        StatRow("版本号", BuildConfig.VERSION_CODE.toString())
        StatRow("构建类型", BuildConfig.BUILD_TYPE)
        StatRow("调试构建", BuildConfig.DEBUG.toString())

        Divider(Modifier.padding(vertical = 8.dp))

        Text(
            text = "本程序通过静态逆向原厂 App（com.yl.milight v1.0.0）还原 BLE 协议后重写，" +
                "与设备固件逐字节兼容，固件无需改动。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "仅供自有设备配套使用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
