"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { createComment } from "@/lib/api/comments";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from "@/components/ui/form";

const commentSchema = z.object({
  markdownSource: z.string().min(1, "Comment cannot be empty"),
});

type CommentValues = z.infer<typeof commentSchema>;

interface CommentFormProps {
  year: string;
  month: string;
  slug: string;
  parentCommentId?: string;
  onSuccess: () => void;
  onCancel?: () => void;
}

export function CommentForm({
  year,
  month,
  slug,
  parentCommentId,
  onSuccess,
  onCancel,
}: CommentFormProps) {
  const form = useForm<CommentValues>({
    resolver: zodResolver(commentSchema),
    defaultValues: { markdownSource: "" },
  });

  async function onSubmit(values: CommentValues) {
    try {
      await createComment(year, month, slug, {
        markdownSource: values.markdownSource,
        parentCommentId,
      });
      form.reset();
      onSuccess();
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.detail
          : "Failed to post comment.";
      toast.error(message);
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
        <FormField
          control={form.control}
          name="markdownSource"
          render={({ field }) => (
            <FormItem>
              <FormControl>
                <Textarea
                  placeholder="Write a comment (Markdown supported)..."
                  rows={3}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="flex gap-2">
          <Button
            type="submit"
            size="sm"
            disabled={form.formState.isSubmitting}
          >
            {form.formState.isSubmitting ? "Posting..." : "Post comment"}
          </Button>
          {onCancel && (
            <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
              Cancel
            </Button>
          )}
        </div>
      </form>
    </Form>
  );
}
