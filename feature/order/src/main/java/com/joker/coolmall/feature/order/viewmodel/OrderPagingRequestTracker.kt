package com.joker.coolmall.feature.order.viewmodel

/**
 * 一次订单分页请求的不可变身份。
 *
 * 每个标签页维护独立序号，旧请求只能与自己的标签页和序号比较。
 */
internal data class OrderPageRequestToken(val tabIndex: Int, val page: Int, val sequence: Long)

/**
 * 维护订单标签页相互隔离的已提交页码和请求序号。
 */
internal class OrderPagingRequestTracker(tabCount: Int, initialPage: Int = 1) {
    private val pageIndices = MutableList(tabCount) { initialPage }
    private val requestSequences = MutableList(tabCount) { 0L }

    fun isValidTab(tabIndex: Int): Boolean = tabIndex in pageIndices.indices

    fun invalidate(tabIndex: Int) {
        requireValidTab(tabIndex)
        requestSequences[tabIndex]++
    }

    fun startRequest(tabIndex: Int, page: Int): OrderPageRequestToken {
        requireValidTab(tabIndex)
        return OrderPageRequestToken(
            tabIndex = tabIndex,
            page = page,
            sequence = ++requestSequences[tabIndex],
        )
    }

    fun isCurrent(token: OrderPageRequestToken): Boolean =
        isValidTab(token.tabIndex) && requestSequences[token.tabIndex] == token.sequence

    fun commit(token: OrderPageRequestToken): Boolean {
        if (!isCurrent(token)) return false
        pageIndices[token.tabIndex] = token.page
        return true
    }

    fun nextPage(tabIndex: Int): Int {
        requireValidTab(tabIndex)
        return pageIndices[tabIndex] + 1
    }

    private fun requireValidTab(tabIndex: Int) {
        require(isValidTab(tabIndex)) { "Invalid order tab index: $tabIndex" }
    }
}
