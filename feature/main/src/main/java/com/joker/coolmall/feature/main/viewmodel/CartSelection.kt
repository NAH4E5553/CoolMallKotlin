package com.joker.coolmall.feature.main.viewmodel

import com.joker.coolmall.core.model.entity.Cart

internal typealias CartSelection = Map<Long, Set<Long>>

internal fun selectAllCartItems(carts: List<Cart>): CartSelection = buildMap {
    carts.forEach { cart ->
        val specIds = cart.spec.map { it.id }.toSet()
        if (specIds.isNotEmpty()) put(cart.goodsId, specIds)
    }
}

internal fun toggleCartItemSelection(currentSelection: CartSelection, goodsId: Long, specId: Long): CartSelection =
    currentSelection.toMutableMap().apply {
        val selectedSpecIds = currentSelection[goodsId].orEmpty().toMutableSet()
        if (!selectedSpecIds.add(specId)) selectedSpecIds.remove(specId)

        if (selectedSpecIds.isEmpty()) {
            remove(goodsId)
        } else {
            put(goodsId, selectedSpecIds)
        }
    }.toMap()

internal fun areAllCartItemsSelected(carts: List<Cart>, selection: CartSelection): Boolean {
    val allItems = carts.flatMap { cart ->
        cart.spec.map { spec -> cart.goodsId to spec.id }
    }
    if (allItems.isEmpty()) return false

    return allItems.all { (goodsId, specId) -> specId in selection[goodsId].orEmpty() }
}

internal fun selectedCartItemCount(selection: CartSelection): Int = selection.values.sumOf(Set<Long>::size)

internal fun selectedCartTotalAmount(carts: List<Cart>, selection: CartSelection): Int = carts.sumOf { cart ->
    val selectedSpecIds = selection[cart.goodsId].orEmpty()
    cart.spec
        .filter { it.id in selectedSpecIds }
        .sumOf { it.price * it.count }
}
