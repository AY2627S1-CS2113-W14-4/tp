package stockholm.order;

import stockholm.inventory.Item;

public class Order {
    OrderState state;
    String dateCreated; // TODO: implement DateTime system for this
    Item item;
    String notes;
}
