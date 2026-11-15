package com.bandwa.openadb.presentation.main.about

/**
 * 「关于软件」页要展示的法律声明文本集中在这里。
 *
 * 为什么必须留着这些：GPL-3.0 §5(a)(b) 要求衍生作品 **显著地** 写明「改过、改了哪一部分、
 * 日期」，并写明「本作品仍以 GPL-3.0 发布」；§5(d) 还要求带交互界面的作品展示
 * Appropriate Legal Notices（合适的法律声明）。上游 README「License」一节把这几点
 * 写得很明确：「衍生作品必须继续以 GPL-3.0 开源，并保留版权与许可证声明」。
 *
 * 所以本页保留上游原来的 **版权行 / 特别感谢 / 贡献者头像 / 赞赏码**，
 * 只在最前面加一段「基于原版修改」的说明 —— 不删原声明，只加自己的。
 */
object LegalNotices {

    /** 页首「基于原版修改」说明。对应 GPL-3.0 §5(a)：说明改了什么、什么时候改的。 */
    const val MODIFIED_NOTICE =
        "本软件基于 bandwa 开源的 PSA 工具箱修改（github.com/band-wa/psa-toolbox，GPL-3.0）。\n" +
            "修改日期：2026-11-16。修改内容：新增「按键映射」「应用管理」两个页面，" +
            "并在公共服务的注入载荷中增加按键映射相关补丁（音乐/地图键长按、会员中心键等）。\n" +
            "本衍生作品同样以 GNU GPL-3.0 发布，源代码随本软件一并提供。"

    /** 上游原来的版权行（原文照抄自上游 `AboutSettingsRoute.kt` 第 125-126 行，不删不改）。 */
    const val UPSTREAM_COPYRIGHT =
        "© 2025 bandwa, YC-Tan, ningning21 and fingdo 版权所有"

    /** 上游原来的致谢（原文照抄，不删不改）。 */
    const val UPSTREAM_THANKS =
        "特别感谢: 逐梦汴梁城 (甲壳虫等详细教程), 卓群 (无偿提供云盘), 戏子 (tasker方控), " +
            "国新 (拨号调试) 和所有间接参与或测试反馈的车友"

    /**
     * 许可证全文摘要（点「GPL-3.0 许可证」按钮弹出来看）。
     *
     * 这里放的是**要点 + 指向**，不是全文：GPL-3.0 全文 35 KB，塞进弹窗没人看得完，
     * 而且 §4 要求的是「随作品提供许可证副本」，我们以 README/LICENSE 文件形式在
     * 源码库里提供（见交付说明「合并步骤」）。
     */
    const val LICENSE_SUMMARY =
        "GNU General Public License v3.0（GPL-3.0）\n\n" +
            "本软件是自由软件：你可以自由使用、修改和分发它，但衍生作品必须继续以 GPL-3.0 开源，" +
            "并保留版权与许可证声明。\n\n" +
            "本软件按「现状」提供，不附带任何担保。\n\n" +
            "完整许可证全文见上游仓库的 LICENSE 文件：\n" +
            "github.com/band-wa/psa-toolbox/blob/main/LICENSE\n" +
            "或 GNU 官方页面：https://www.gnu.org/licenses/gpl-3.0.html"
}
