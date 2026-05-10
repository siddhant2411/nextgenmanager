-- Create message_template table
CREATE TABLE IF NOT EXISTS message_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    subject VARCHAR(500),
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    category VARCHAR(20) DEFAULT 'ENQUIRY',
    is_default BOOLEAN DEFAULT FALSE,
    deleted_date TIMESTAMP,
    created_by VARCHAR(255),
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed default templates for Indian MSME sales
INSERT INTO message_template (name, body, channel, category, is_default) VALUES
('Enquiry Intro', 'Hello {{contactPerson}},

Thank you for your interest in our products. This is regarding your Enquiry {{enqNo}} for {{opportunityName}}.

We would love to understand your requirements better and provide you with the best solution.

When would be a convenient time to discuss?

Regards,
{{companyName}}', 'WHATSAPP', 'ENQUIRY', true),

('Follow-up Reminder', 'Hello {{contactPerson}},

Just following up on our earlier conversation regarding Enquiry {{enqNo}} - {{opportunityName}}.

Please let us know if you have any questions or need any additional information.

Looking forward to hearing from you.

Best regards,
{{companyName}}', 'WHATSAPP', 'FOLLOW_UP', true),

('Quotation Sent', 'Dear {{contactPerson}},

We are pleased to share our quotation for {{opportunityName}} (Ref: {{enqNo}}).

Please review and let us know if you need any modifications or have questions.

We are confident our solution will meet your requirements.

Warm regards,
{{companyName}}', 'WHATSAPP', 'QUOTATION', true),

('Email - Enquiry Acknowledgment', 'Dear {{contactPerson}},

Thank you for reaching out to us regarding {{opportunityName}}.

Your enquiry has been registered with reference number {{enqNo}}. Our team is reviewing your requirements and will get back to you within 24 hours with a detailed proposal.

If you have any immediate questions, please do not hesitate to contact us.

Best regards,
{{companyName}}', 'EMAIL', 'ENQUIRY', true),

('Email - Follow Up', 'Dear {{contactPerson}},

I hope this email finds you well. I am writing to follow up on our recent discussion about {{opportunityName}} (Ref: {{enqNo}}).

We understand that evaluating options takes time, and we are here to assist you with any information you may need to make the best decision.

Could we schedule a brief call this week to address any pending questions?

Best regards,
{{companyName}}', 'EMAIL', 'FOLLOW_UP', true);
