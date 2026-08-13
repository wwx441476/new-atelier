package com.example.atelier.document.extract;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PdfParagraphSplitterTest {

    @Test
    public void splitsChapterArticleAndNumberedListWithoutBlankLines() {
        String page = ""
                + "第五章 信息披露与投资者管理\n"
                + "第十六条 常规信息披露\n"
                + "管理人应当按照合同约定履行信披义务。\n"
                + "第十七条 净值异常专项告知\n"
                + "1. 触发黄色及以上预警的，当日告知；\n"
                + "2. 触发红色预警的，同步公告；\n"
                + "3. 建立投诉应对机制。\n"
                + "第十八条 舆情管控\n"
                + "建立舆情监测机制。";

        List<String> parts = PdfParagraphSplitter.splitTexts(page);

        assertTrue(parts.size() >= 7);
        assertEquals("第五章 信息披露与投资者管理", parts.get(0));
        assertEquals("第十六条 常规信息披露", parts.get(1));
        assertEquals("管理人应当按照合同约定履行信披义务。", parts.get(2));
        assertEquals("第十七条 净值异常专项告知", parts.get(3));
        assertEquals("1. 触发黄色及以上预警的，当日告知；", parts.get(4));
        assertTrue(parts.contains("第十八条 舆情管控"));
    }

    @Test
    public void blankLineAlsoFlushesParagraph() {
        String page = "第一段内容。\n\n第二段内容。";
        List<String> parts = PdfParagraphSplitter.splitTexts(page);
        assertEquals(2, parts.size());
        assertEquals("第一段内容。", parts.get(0));
        assertEquals("第二段内容。", parts.get(1));
    }

    @Test
    public void normalizesPdfBulletGlyphIntoListItem() {
        String page = ""
                + "。配合风控完成风险说明。\n"
                + "2. 风险管理部（牵头部门）\n"
                + "。每日校验净值数据。\n"
                + "第二章 净值监控";
        List<String> parts = PdfParagraphSplitter.splitTexts(page);
        assertEquals("· 配合风控完成风险说明。", parts.get(0));
        assertEquals("2. 风险管理部（牵头部门）", parts.get(1));
        assertEquals("· 每日校验净值数据。", parts.get(2));
        assertEquals("第二章 净值监控", parts.get(3));
    }

    @Test
    public void dropsAdjacentDuplicateTitleFromFakeBold() {
        String page = ""
                + "资产净值预警管理办法\n"
                + "资产净值预警管理办法\n"
                + "第一章 总则\n"
                + "第一条 制定目的\n"
                + "为规范公司各类资管产品。";
        List<String> parts = PdfParagraphSplitter.splitTexts(page);
        assertEquals(4, parts.size());
        assertEquals("资产净值预警管理办法", parts.get(0));
        assertEquals("第一章 总则", parts.get(1));
        assertEquals("第一条 制定目的", parts.get(2));
    }

    @Test
    public void joinsPdfSoftWrapInsideNumberedListItem() {
        // 模拟版心换行把「处置」「准则」「方案」从中间切断
        String page = ""
                + "第三条 管理原则\n"
                + "3. 权责清晰、闭环管理：明确投研、风控职责，形成「预警 — 响应 —\n"
                + "上报 — 处\n"
                + "置 — 复盘全闭环；\n"
                + "4. 客观公允、依法合规：严格依据估值政策、会计准\n"
                + "则、监管披露要求；\n"
                + "1. 投资管理部（投研 / 基金经理）\n"
                + "。预警触发后第一时间排查净值异动原因，制定应对与量化方\n"
                + "案；\n"
                + "。配合风控完成风险说明。\n"
                + "2. 风险管理部（牵头部门）";
        List<String> parts = PdfParagraphSplitter.splitTexts(page);

        assertEquals("第三条 管理原则", parts.get(0));
        assertEquals("3. 权责清晰、闭环管理：明确投研、风控职责，形成「预警 — 响应 — 上报 — 处置 — 复盘全闭环；",
                parts.get(1));
        assertEquals("4. 客观公允、依法合规：严格依据估值政策、会计准则、监管披露要求；",
                parts.get(2));
        assertEquals("1. 投资管理部（投研 / 基金经理）", parts.get(3));
        assertEquals("· 预警触发后第一时间排查净值异动原因，制定应对与量化方案；", parts.get(4));
        assertEquals("· 配合风控完成风险说明。", parts.get(5));
        assertEquals("2. 风险管理部（牵头部门）", parts.get(6));
    }

    @Test
    public void joinsSoftWrapInBodyParagraphWithoutSpaceForCjk() {
        String page = ""
                + "第一条 制定目的\n"
                + "为规范公司各类资管产品、自有投资资产、基金 / 理财类资产净值监\n"
                + "控、预警、处置全流程管理，特制定本办法。";
        List<String> parts = PdfParagraphSplitter.splitTexts(page);
        assertEquals(2, parts.size());
        assertEquals("第一条 制定目的", parts.get(0));
        assertEquals("为规范公司各类资管产品、自有投资资产、基金 / 理财类资产净值监控、预警、处置全流程管理，特制定本办法。",
                parts.get(1));
    }
}
