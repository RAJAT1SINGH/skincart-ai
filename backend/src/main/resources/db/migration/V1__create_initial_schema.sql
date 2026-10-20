CREATE TABLE skin_analysis (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    original_image_reference VARCHAR(2048),
    overall_score NUMERIC(5, 2),
    provider_task_id VARCHAR(255),
    provider_name VARCHAR(100),
    error_code VARCHAR(100),
    error_message VARCHAR(2048),
    CONSTRAINT ck_skin_analysis_status
        CHECK (status IN ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_skin_analysis_created_at ON skin_analysis (created_at DESC);

CREATE TABLE skin_concern (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    concern_code VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    severity VARCHAR(40),
    CONSTRAINT fk_skin_concern_analysis
        FOREIGN KEY (analysis_id) REFERENCES skin_analysis (id),
    CONSTRAINT uk_skin_concern_analysis_code UNIQUE (analysis_id, concern_code)
);

CREATE TABLE ingredient (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    description VARCHAR(2048)
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(160),
    description VARCHAR(2048)
);

CREATE TABLE product_ingredient (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    CONSTRAINT fk_product_ingredient_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_ingredient_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredient (id),
    CONSTRAINT uk_product_ingredient_pair UNIQUE (product_id, ingredient_id)
);
