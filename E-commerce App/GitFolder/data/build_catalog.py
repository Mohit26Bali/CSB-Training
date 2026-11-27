import json
import math
import os
import random
import re
from datetime import datetime, timedelta
HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, 'dummyjson-products.json')
OUTPUT = os.path.join(HERE, '..', 'backend', 'src', 'main', 'resources', 'db', 'migration', 'V2__catalog_data.sql')
USD_TO_INR = 85
ADMIN_HASH = '$2a$10$JYJfSMksr9uYDBle4MTgVOfHTsRSifvnTg2vCve8R3omai4EBLn8a'
CUSTOMER_HASH = '$2a$10$U1UZf48M3ankEIIemOvjfO8EoW/2zUkw3zxP2uFt0Ifqvm4mQjK02'
CATALOG_DATE = datetime(2026, 10, 20, 10, 0, 0)
TREE = [('Electronics', 'electronics', [('Smartphones', 'smartphones'), ('Laptops', 'laptops'), ('Tablets', 'tablets'), ('Headphones & Earphones', 'headphones-earphones'), ('Smart Speakers', 'smart-speakers'), ('Smart Watches', 'smart-watches'), ('Chargers & Power Banks', 'chargers-power-banks'), ('Phone Cases', 'phone-cases'), ('Camera Accessories', 'camera-accessories')]), ("Men's Fashion", 'men', [('Shirts', 'mens-shirts'), ('Footwear', 'mens-footwear'), ('Watches', 'mens-watches')]), ("Women's Fashion", 'women', [('Tops', 'womens-tops'), ('Dresses', 'womens-dresses'), ('Footwear', 'womens-footwear'), ('Handbags', 'handbags'), ('Jewellery', 'jewellery'), ('Watches', 'womens-watches')]), ('Accessories', 'accessories', [('Sunglasses', 'sunglasses')]), ('Beauty & Personal Care', 'beauty-personal-care', [('Makeup', 'makeup'), ('Fragrances', 'fragrances'), ('Skin Care', 'skin-care')]), ('Home & Furniture', 'home-furniture', [('Furniture', 'furniture'), ('Home Decor', 'home-decor')]), ('Kitchen & Dining', 'kitchen-dining', [('Cookware', 'cookware'), ('Kitchen Appliances', 'kitchen-appliances'), ('Kitchen Tools', 'kitchen-tools'), ('Dining & Serveware', 'dining-serveware')]), ('Grocery', 'grocery', [('Fruits & Vegetables', 'fruits-vegetables'), ('Dairy & Desserts', 'dairy-desserts'), ('Beverages', 'beverages'), ('Meat & Seafood', 'meat-seafood'), ('Staples & Cooking', 'staples-cooking'), ('Health Supplements', 'health-supplements'), ('Household Essentials', 'household-essentials'), ('Pet Supplies', 'pet-supplies')]), ('Sports & Fitness', 'sports-fitness', [('Cricket', 'cricket'), ('Ball Sports', 'ball-sports'), ('Racket Sports', 'racket-sports'), ('Golf', 'golf')]), ('Automotive', 'automotive', [('Cars', 'cars'), ('Motorcycles', 'motorcycles')])]
DIRECT = {'smartphones': 'smartphones', 'laptops': 'laptops', 'tablets': 'tablets', 'mens-shirts': 'mens-shirts', 'mens-shoes': 'mens-footwear', 'mens-watches': 'mens-watches', 'tops': 'womens-tops', 'womens-dresses': 'womens-dresses', 'womens-shoes': 'womens-footwear', 'womens-bags': 'handbags', 'womens-jewellery': 'jewellery', 'womens-watches': 'womens-watches', 'sunglasses': 'sunglasses', 'beauty': 'makeup', 'fragrances': 'fragrances', 'skin-care': 'skin-care', 'furniture': 'furniture', 'home-decoration': 'home-decor', 'vehicle': 'cars', 'motorcycle': 'motorcycles'}
BY_TAG = {'mobile-accessories': [({'wireless earphones', 'over-ear headphones'}, 'headphones-earphones'), ({'smart speakers'}, 'smart-speakers'), ({'smartwatches'}, 'smart-watches'), ({'chargers', 'wireless chargers', 'power banks'}, 'chargers-power-banks'), ({'phone accessories'}, 'phone-cases'), ({'camera accessories', 'selfie accessories'}, 'camera-accessories')], 'kitchen-accessories': [({'cookware'}, 'cookware'), ({'kitchen appliances'}, 'kitchen-appliances'), ({'drinkware', 'dinnerware', 'serveware'}, 'dining-serveware'), (None, 'kitchen-tools')], 'groceries': [({'fruits', 'vegetables'}, 'fruits-vegetables'), ({'dairy', 'desserts'}, 'dairy-desserts'), ({'beverages'}, 'beverages'), ({'meat', 'seafood'}, 'meat-seafood'), ({'cooking essentials', 'grains', 'condiments'}, 'staples-cooking'), ({'health supplements'}, 'health-supplements'), ({'household essentials'}, 'household-essentials'), ({'pet supplies'}, 'pet-supplies')], 'sports-accessories': [({'cricket'}, 'cricket'), ({'tennis', 'badminton'}, 'racket-sports'), ({'golf'}, 'golf'), (None, 'ball-sports')]}
APPAREL_SIZES = ['S', 'M', 'L', 'XL', 'XXL']
MENS_SHOE_SIZES = ['UK 7', 'UK 8', 'UK 9', 'UK 10', 'UK 11']
WOMENS_SHOE_SIZES = ['UK 4', 'UK 5', 'UK 6', 'UK 7', 'UK 8']

def slugify(text):
    return re.sub('[^a-z0-9]+', '-', text.lower()).strip('-')

def sql(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, datetime):
        return "'" + value.strftime('%Y-%m-%d %H:%M:%S') + "'"
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"

def shop_price(amount):
    step = 10 if amount < 10000 else 100
    return max(9, int(round(amount / step)) * step - 1)

def leaf_for(product):
    if product['category'] in DIRECT:
        return DIRECT[product['category']]
    tags = set(product['tags'])
    for wanted, slug in BY_TAG[product['category']]:
        if wanted is None or tags & wanted:
            return slug
    raise ValueError('No category for ' + product['title'])

def variant_plan(leaf):
    if leaf in ('mens-shirts', 'womens-tops', 'womens-dresses'):
        return ('SIZE', [(s, 1.0) for s in APPAREL_SIZES])
    if leaf == 'mens-footwear':
        return ('SIZE', [(s, 1.0) for s in MENS_SHOE_SIZES])
    if leaf == 'womens-footwear':
        return ('SIZE', [(s, 1.0) for s in WOMENS_SHOE_SIZES])
    if leaf in ('smartphones', 'tablets'):
        return ('STORAGE', [('128 GB', 1.0), ('256 GB', 1.15), ('512 GB', 1.35)])
    if leaf == 'laptops':
        return ('CONFIGURATION', [('8 GB RAM / 512 GB SSD', 1.0), ('16 GB RAM / 1 TB SSD', 1.2)])
    return ('NONE', [('Standard', 1.0)])

def sku_suffix(label):
    return re.sub('[^A-Z0-9]', '', label.upper().replace('RAM', '').replace('SSD', ''))[:10] or 'STD'

def main():
    products = json.load(open(SOURCE, encoding='utf-8'))['products']
    rnd = random.Random(42)
    out = ['-- Generated by data/build_catalog.py from the DummyJSON product catalogue. Do not edit by hand.', '']
    leaf_products = {}
    for p in products:
        leaf_products.setdefault(leaf_for(p), []).append(p)
    category_id, rows = ({}, [])
    next_id = 1
    for top_order, (top_name, top_slug, leaves) in enumerate(TREE, start=1):
        top_image = next((leaf_products[s][0]['thumbnail'] for _, s in leaves if s in leaf_products))
        category_id[top_slug] = next_id
        rows.append((next_id, top_name, top_slug, None, top_image, top_order))
        parent = next_id
        next_id += 1
        for leaf_order, (leaf_name, leaf_slug) in enumerate(leaves, start=1):
            category_id[leaf_slug] = next_id
            rows.append((next_id, leaf_name, leaf_slug, parent, leaf_products[leaf_slug][0]['thumbnail'], leaf_order))
            next_id += 1
    out.append('INSERT INTO categories (id, name, slug, parent_id, image_url, sort_order) VALUES')
    out.append(',\n'.join(('(' + ', '.join((sql(v) for v in r)) + ')' for r in rows)) + ';\n')
    brand_names = sorted({p['brand'] for p in products if p.get('brand')}, key=str.lower)
    brand_id = {name: i for i, name in enumerate(brand_names, start=1)}
    out.append('INSERT INTO brands (id, name, slug) VALUES')
    out.append(',\n'.join((f'({i}, {sql(n)}, {sql(slugify(n))})' for n, i in brand_id.items())) + ';\n')
    users = [('Store Admin', 'admin@cartnest.in', ADMIN_HASH, 'ADMIN'), ('Riya Verma', 'riya@example.com', CUSTOMER_HASH, 'CUSTOMER')]
    reviewer_id = {}
    for p in products:
        for r in p['reviews']:
            name = r['reviewerName']
            if name not in reviewer_id:
                users.append((name, slugify(name).replace('-', '.') + '@example.com', CUSTOMER_HASH, 'CUSTOMER'))
                reviewer_id[name] = len(users)
    out.append('INSERT INTO users (id, full_name, email, password_hash, role, created_at) VALUES')
    out.append(',\n'.join((f'({i}, {sql(n)}, {sql(e)}, {sql(h)}, {sql(r)}, {sql(CATALOG_DATE - timedelta(days=200))})' for i, (n, e, h, r) in enumerate(users, start=1))) + ';\n')
    product_rows, images, variants, specs, tags, reviews = ([], [], [], [], [], [])
    used_slugs = set()
    for pid, p in enumerate(products, start=1):
        leaf = leaf_for(p)
        slug = slugify(p['title'])
        while slug in used_slugs:
            slug += '-2'
        used_slugs.add(slug)
        variant_type, plan = variant_plan(leaf)
        base_price = shop_price(p['price'] * USD_TO_INR)
        discount = p['discountPercentage']
        variant_rows = []
        for position, (label, factor) in enumerate(plan, start=1):
            price = base_price if factor == 1.0 else shop_price(base_price * factor)
            mrp = max(price, shop_price(price / (1 - discount / 100)))
            share = p['stock'] // len(plan) + (1 if position <= p['stock'] % len(plan) else 0)
            if variant_type == 'SIZE' and position == len(plan) and (pid % 5 == 0):
                share = 0
            sku = (p['sku'] + '-' + sku_suffix(label))[:40]
            variant_rows.append((pid, sku, label, price, mrp, share, position))
        variants.extend(variant_rows)
        cheapest = min(variant_rows, key=lambda v: v[3])
        price, mrp = (cheapest[3], cheapest[4])
        discount_percent = math.floor((mrp - price) * 100 / mrp)
        unique_reviews = {}
        for r in p['reviews']:
            unique_reviews.setdefault(reviewer_id[r['reviewerName']], r)
        ratings = [r['rating'] for r in unique_reviews.values()]
        rating_avg = round(sum(ratings) / len(ratings), 2) if ratings else 0
        created = CATALOG_DATE - timedelta(days=rnd.randint(1, 180), minutes=rnd.randint(0, 1440))
        d = p['dimensions']
        product_rows.append((pid, p['title'], slug, p['description'], brand_id.get(p.get('brand')), category_id[leaf], variant_type, price, mrp, discount_percent, rating_avg, len(ratings), p['thumbnail'], p.get('warrantyInformation'), p.get('shippingInformation'), p.get('returnPolicy'), created, created))
        images.extend(((pid, url, i) for i, url in enumerate(p['images'], start=1)))
        specs.append((pid, 'Dimensions (W x H x D)', f"{d['width']} x {d['height']} x {d['depth']} cm", 1))
        specs.append((pid, 'SKU', p['sku'], 2))
        tags.extend(((pid, t) for t in sorted(set(p['tags']))))
        for user, r in unique_reviews.items():
            reviews.append((pid, user, r['rating'], r['comment'], created + timedelta(days=rnd.randint(2, 40), minutes=rnd.randint(0, 1440))))

    def insert(table, columns, values):
        out.append(f"INSERT INTO {table} ({', '.join(columns)}) VALUES")
        out.append(',\n'.join(('(' + ', '.join((sql(v) for v in row)) + ')' for row in values)) + ';\n')
    insert('products', ['id', 'title', 'slug', 'description', 'brand_id', 'category_id', 'variant_type', 'price', 'mrp', 'discount_percent', 'rating_avg', 'rating_count', 'thumbnail_url', 'warranty', 'shipping_info', 'return_policy', 'created_at', 'updated_at'], product_rows)
    insert('product_images', ['product_id', 'url', 'position'], images)
    insert('product_variants', ['product_id', 'sku', 'label', 'price', 'mrp', 'stock', 'position'], variants)
    insert('product_specs', ['product_id', 'name', 'value', 'position'], specs)
    insert('product_tags', ['product_id', 'tag'], tags)
    insert('reviews', ['product_id', 'user_id', 'rating', 'comment', 'created_at'], reviews)
    with open(OUTPUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out))
    print(f'{len(rows)} categories, {len(brand_id)} brands, {len(users)} users, {len(product_rows)} products, {len(variants)} variants, {len(images)} images, {len(reviews)} reviews -> {os.path.normpath(OUTPUT)}')
if __name__ == '__main__':
    main()
