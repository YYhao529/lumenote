package com.deepsea.lumenote.aigc.config;

import com.deepsea.lumenote.aigc.tool.NoteTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置
 *
 * <p>ChatClient 是 Spring AI 推荐的高层 API：统一了提示词、消息、结构化输出、工具调用等能力。
 * 系统提示词放在这里，业务侧只关心用户输入。</p>
 *
 * <p>注意：未配置 API Key 时 Spring AI 不会创建 DeepSeekChatModel，这里用
 * {@link ConditionalOnBean} 保证服务仍能启动，接口层再给出友好提示。</p>
 */
@Configuration
public class ChatClientConfig {

    /**
     * 拾光笔记智能助手的人设
     */
    private static final String SYSTEM_PROMPT = """
            你是「拾光笔记」App 里的智能助手，名字叫小拾。
            拾光笔记是一个记录生活灵感、发现同好的图文笔记社区。

            你的职责：
            1. 帮用户写笔记文案、起标题、想话题和标签；
            2. 帮用户润色、扩写、缩写、改写笔记内容；
            3. 回答与记录生活、摄影、穿搭、探店、学习笔记相关的问题；
            4. 帮用户规划内容选题。

            你可以调用工具读取用户自己的笔记：
            - 用户说“我的笔记 / 我写的 / 之前发的那篇 / 关于 X 的那篇”时，先用 searchMyNotes 找；
              需要改写、总结、润色正文时，再用 getNoteDetail 取全文；
            - 不要凭空猜测用户的笔记内容，找不到就如实说“没找到相关笔记”；
            - 工具只能读到当前用户自己的数据，不要向用户索要别人的笔记 ID。

            回答要求：
            - 用中文，语气亲切自然，不要端着；
            - 直接给结果，少说套话和免责声明；
            - 写文案时给 2~3 个不同风格的版本，便于用户挑选；
            - 内容较长时用换行或短列表，便于直接复制；
            - 不要编造事实，拿不准就说明。
            """;

    /**
     * 注意：这里不能用 {@code @ConditionalOnBean}。
     * 用户配置类先于自动配置求值，那一刻 DeepSeekChatModel 还没注册，条件必然不成立，
     * 结果 ChatClient 永远建不出来。改为在 Bean 方法里用 ObjectProvider 延迟取，
     * 拿不到（没配 API Key）时返回 null，Spring 会当成"没有这个 Bean"，服务照常启动。
     */
    @Bean
    public ChatClient chatClient(ObjectProvider<DeepSeekChatModel> chatModelProvider, NoteTools noteTools) {
        DeepSeekChatModel deepSeekChatModel = chatModelProvider.getIfAvailable();
        if (deepSeekChatModel == null) {
            return null;
        }
        return ChatClient.builder(deepSeekChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                // 注册「函数调用」工具：读用户自己的笔记（只读，身份取自登录上下文）
                .defaultTools(noteTools)
                .build();
    }
}
